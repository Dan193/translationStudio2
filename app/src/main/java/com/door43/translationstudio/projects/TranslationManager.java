package com.door43.translationstudio.projects;

import android.text.Editable;
import android.text.SpannedString;
import android.util.Log;

import com.door43.translationstudio.MainApplication;
import com.door43.translationstudio.R;
import com.door43.translationstudio.SettingsActivity;
import com.door43.translationstudio.events.SecurityKeysSubmittedEvent;
import com.door43.translationstudio.git.Repo;
import com.door43.translationstudio.git.tasks.ProgressCallback;
import com.door43.translationstudio.git.tasks.repo.CommitTask;
import com.door43.translationstudio.git.tasks.repo.PushTask;
import com.door43.translationstudio.user.ProfileManager;
import com.door43.util.FileUtilities;
import com.door43.translationstudio.util.AppContext;
import com.door43.util.TCPClient;
import com.door43.util.Logger;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * This class handles the storage of translated content.
 */
public class TranslationManager implements TCPClient.TcpListener {
    private TranslationManager me = this;
    private MainApplication mContext;
    private final String TAG = "TranslationManager";
    private final String mParentProjectSlug = "uw"; //  NOTE: not sure if this will ever need to be dynamic
    private TCPClient mTcpClient;
    private Frame mFrame;
    private Editable mText;

    public TranslationManager(MainApplication context) {
        mContext = context;
    }

    /**
     * Initiates a git sync with the server. This will forcebly push all local changes to the server
     * and discard any discrepencies.
     */
    public void syncSelectedProject() {
        if(!mContext.hasRegisteredKeys()) {
            mContext.showProgressDialog(R.string.loading);
            // set up a tcp connection
            if(mTcpClient == null) {
                mTcpClient = new TCPClient(mContext.getUserPreferences().getString(SettingsActivity.KEY_PREF_AUTH_SERVER, mContext.getResources().getString(R.string.pref_default_auth_server)), Integer.parseInt(mContext.getUserPreferences().getString(SettingsActivity.KEY_PREF_AUTH_SERVER_PORT, mContext.getResources().getString(R.string.pref_default_auth_server_port))), me);
            } else {
                // TODO: update the sever and port if they have changed... Not sure if this task is applicable now
            }
            // connect to the server so we can submit our key
            mTcpClient.connect();
        } else {
            pushSelectedProjectRepo();
        }
    }

    /**
     * Stages a new translation to be saved. This is usually called whenever the translation in a frame changes
     * @param frame the frame for which the translation changed
     * @param text the raw translation input
     */
    public void stageTranslation(Frame frame, Editable text) {
        this.mFrame = frame;
        this.mText = text;
    }

    /**
     * Saves the staged translation
     */
    public void commitTranslation() {
        if(mFrame != null && mText != null) {
            Frame frame = mFrame;
            mFrame = null;
            Editable text = mText;
            mText = null;

            // compile the translation
            StringBuilder compiledString = new StringBuilder();
            int next;
            int lastIndex = 0;
            for (int i = 0; i < text.length(); i = next) {
                next = text.nextSpanTransition(i, text.length(), SpannedString.class);
                SpannedString[] verses = text.getSpans(i, next, SpannedString.class);
                for (SpannedString s : verses) {
                    int sStart = text.getSpanStart(s);
                    int sEnd = text.getSpanEnd(s);
                    // attach preceeding text
                    if (lastIndex >= text.length() | sStart >= text.length()) {
                        Logger.e(this.getClass().getName(), "out of bounds");
                    }
                    compiledString.append(text.toString().substring(lastIndex, sStart));
                    // explode span
                    compiledString.append(s.toString());
                    lastIndex = sEnd;
                }
            }
            // grab the last bit of text
            compiledString.append(text.toString().substring(lastIndex, text.length()));

            frame.setTranslation(compiledString.toString().trim());
            frame.save();
        }
    }

    /**
     * Pushes the currently selected project+language repo to the server
     */
    private void pushSelectedProjectRepo() {
        Project p = AppContext.projectManager().getSelectedProject();

        if(p.translationIsReady()) {
            Logger.i(this.getClass().getName(), "Publishing project " + p.getId() + " to the server");
        }

        final String remotePath = getRemotePath(p, p.getSelectedTargetLanguage());
        final Repo repo = new Repo(p.getRepositoryPath());

        CommitTask add = new CommitTask(repo, ".", new CommitTask.OnAddComplete() {
            @Override
            public void success() {
                PushTask push = new PushTask(repo, remotePath, true, true, new ProgressCallback(R.string.push_msg_init));
                push.executeTask();
                // TODO: we need to check the errors from the push task. If auth fails then we need to re-register the ssh keys.
            }

            @Override
            public void error(Throwable e) {
                mContext.showException(e, R.string.error_git_stage);
            }
        });
        add.executeTask();

        // send the latest profile info to the server as well
        ProfileManager.push();
    }

    /**
     * Generates the remote path for a local repo
     * @param project
     * @param lang
     * @return
     */
    private String getRemotePath(Project project, Language lang) {
        String server = mContext.getUserPreferences().getString(SettingsActivity.KEY_PREF_GIT_SERVER, mContext.getResources().getString(R.string.pref_default_git_server));
        return server + ":tS/" + AppContext.udid() + "/" + mParentProjectSlug + "-" + project.getId() + "-" + lang.getId();
    }

    /**
     * Submits the client public ssh key to the server so we can push updates
     */
    private void registerSSHKeys() {
        if(mContext.hasKeys()) {
            JSONObject json = new JSONObject();
            try {
                String key = FileUtilities.getStringFromFile(mContext.getPublicKey().getAbsolutePath()).trim();
                json.put("key", key);
                json.put("udid", AppContext.udid());
                // TODO: provide support for using user names
//                json.put("username", "");
                Log.d(TAG, json.toString());
                mContext.showProgressDialog(R.string.submitting_security_keys);
                mTcpClient.sendMessage(json.toString());
            } catch (JSONException e) {
                mContext.showException(e);
            } catch (Exception e) {
                mContext.showException(e);
            }
        } else {
            mContext.closeProgressDialog();
            mContext.showException(new Throwable(mContext.getResources().getString(R.string.error_missing_ssh_keys)));
        }
    }

    @Override
    public void onConnectionEstablished() {
        // submit key to the server
        registerSSHKeys();
    }

    @Override
    public void onMessageReceived(String message) {
        // check if we get an ok message from sending ssh keys to the server
        mContext.closeProgressDialog();
        try {
            JSONObject json = new JSONObject(message);
            if(json.has("ok")) {
                mContext.setHasRegisteredKeys(true);
                AppContext.getEventBus().post(new SecurityKeysSubmittedEvent());
            } else {
                mContext.showException(new Throwable(json.getString("error")));
            }
        } catch (JSONException e) {
            mContext.showException(e);
        }
        mTcpClient.stop();
    }

    @Override
    public void onError(Throwable t) {
        mContext.showException(t);
    }
}
