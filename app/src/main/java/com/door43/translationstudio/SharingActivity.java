package com.door43.translationstudio;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.FileProvider;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.door43.translationstudio.device2device.DeviceToDeviceActivity;
import com.door43.translationstudio.dialogs.ProjectTranslationImportApprovalDialog;
import com.door43.translationstudio.filebrowser.FileBrowserActivity;
import com.door43.translationstudio.projects.Project;
import com.door43.translationstudio.projects.Sharing;
import com.door43.translationstudio.projects.imports.ProjectImport;
import com.door43.translationstudio.util.AppContext;
import com.door43.util.threads.ThreadableUI;
import com.door43.translationstudio.util.ToolAdapter;
import com.door43.translationstudio.util.ToolItem;
import com.door43.translationstudio.util.TranslatorBaseActivity;
import com.door43.util.Logger;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;


public class SharingActivity extends TranslatorBaseActivity {
    private ArrayList<ToolItem> mSharingTools = new ArrayList<ToolItem>();
    private ToolAdapter mAdapter;
    private static int IMPORT_PROJECT_FROM_SD_REQUEST = 0;
    ProgressDialog mProgressDialog;
//    private static int IMPORT_DOKUWIKI_FROM_SD_REQUEST = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sharing);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mProgressDialog = new ProgressDialog(SharingActivity.this);

        // hook up list view
        ListView list = (ListView)findViewById(R.id.sharingListView);
        mAdapter = new ToolAdapter(mSharingTools, this);
        list.setAdapter(mAdapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (mSharingTools.size() > i && i >= 0) {
                    ToolItem tool = mSharingTools.get(i);
                    // execute the sharing action
                    if (tool.isEnabled()) {
                        tool.getAction().run();
                    } else {
                        app().showToastMessage(tool.getDisabledNotice());
                    }
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        mProgressDialog.setMessage(getResources().getString(R.string.loading));
        mProgressDialog.show();

        // stage and commit changes to the current project
        Project p = AppContext.projectManager().getSelectedProject();
        if(p != null) {
            p.commit(new Project.OnCommitComplete() {
                @Override
                public void success() {
                    init();
                    mProgressDialog.dismiss();
                }

                @Override
                public void error(Throwable e) {
                    mProgressDialog.dismiss();
                    AppContext.context().showToastMessage(R.string.project_share_exception);
                    finish();
                }
            });
        } else {
            init();
            mProgressDialog.dismiss();
        }
    }

    private void init() {
        // TRICKY: this project may very well be null
        final Project p = AppContext.projectManager().getSelectedProject();
        final File internalDestDir = new File(getCacheDir(), "sharing/");

        internalDestDir.mkdirs();

        // load export format
        String exportFormt = AppContext.context().getUserPreferences().getString(SettingsActivity.KEY_PREF_EXPORT_FORMAT, AppContext.context().getResources().getString(R.string.pref_default_export_format));
        final boolean exportAsProject = exportFormt.equals("project");
        final boolean exportAsDokuwiki = exportFormt.equals("dokuwiki");

        int descriptionResource = 0;
        if (exportAsProject) {
            descriptionResource = R.string.export_as_project;
        } else if (exportAsDokuwiki) {
            descriptionResource = R.string.export_as_dokuwiki;
        }

        mSharingTools.clear();

        // define sharing tools
        boolean exportToAppEnabled = true;
        String exportToAppMessage = getResources().getString(R.string.missing_external_storage);
        if(p == null) {
            // TODO: eventually this export tool needs to allow the user to choose which project(s) to export. Then we'll just need to check if there are any translations available in the current projects
            exportToAppEnabled = false;
            exportToAppMessage = getResources().getString(R.string.choose_a_project);
        }
        mSharingTools.add(new ToolItem(getResources().getString(R.string.export_to_app), getResources().getString(descriptionResource), R.drawable.ic_icon_export_app, new ToolItem.ToolAction() {
            @Override
            public void run() {
                mProgressDialog.setMessage(getResources().getString(R.string.exporting_project));
                mProgressDialog.show();
                new ThreadableUI(SharingActivity.this) {

                    @Override
                    public void onStop() {

                    }

                    @Override
                    public void run() {
                        try {
                            String archivePath;

                            if (exportAsDokuwiki) {
                                archivePath = Sharing.exportDW(p);
                            } else {
                                archivePath = Sharing.export(p);
                            }
                            File archiveFile = new File(archivePath);
                            if(archiveFile.exists()) {
                                File output = new File(internalDestDir, archiveFile.getName());

                                // copy exported archive to the sharing directory
                                FileUtils.copyFile(archiveFile, output);

                                // share
                                if (output.exists() && output.isFile()) {
                                    Uri u = FileProvider.getUriForFile(SharingActivity.this, "com.door43.translationstudio.fileprovider", output);
                                    Intent i = new Intent(Intent.ACTION_SEND);
                                    i.setType("application/zip");
                                    i.putExtra(Intent.EXTRA_STREAM, u);
                                    startActivity(Intent.createChooser(i, "Email:"));
                                } else {
                                    app().showToastMessage(R.string.project_archive_missing);
                                }
                            } else {
                                app().showToastMessage(R.string.project_archive_missing);
                            }
                        } catch (IOException e) {
                            app().showException(e);
                        }
                    }

                    @Override
                    public void onPostExecute() {
                        mProgressDialog.dismiss();
                    }
                }.start();
            }
        }, exportToAppEnabled, exportToAppMessage));


        boolean externalMediaAvailable = AppContext.isExternalMediaAvailable();
        String exportToSDMessage = getResources().getString(R.string.missing_external_storage);
        if(p == null) {
            // TODO: eventually this export tool needs to allow the user to choose which project(s) to export. Then we'll just need to check if there are any translations available in the current projects
            externalMediaAvailable = false;
            exportToSDMessage = getResources().getString(R.string.choose_a_project);
        }
        mSharingTools.add(new ToolItem(getResources().getString(R.string.export_to_sd), getResources().getString(descriptionResource), R.drawable.ic_icon_export_sd, new ToolItem.ToolAction() {
            @Override
            public void run() {
                final ProgressDialog dialog = new ProgressDialog(SharingActivity.this);
                dialog.setMessage(getResources().getString(R.string.loading));
                dialog.setCancelable(false);
                dialog.setCanceledOnTouchOutside(false);
                dialog.show();
                ThreadableUI thread = new ThreadableUI(SharingActivity.this) {
                    @Override
                    public void onStop() {

                    }

                    @Override
                    public void run() {
                        // TODO: allow the user to choose which projects to export
                        String library = Sharing.generateLibrary(AppContext.projectManager().getProjects());

                        // try to locate the removable sd card
                        if(AppContext.isExternalMediaAvailable()) {
                            try {
                                File externalDestDir = AppContext.getPublicDownloadsDirectory();
                                String archivePath;

                                // export the project
                                // TODO: we need to allow the user to choose which project(s) to export
                                if (exportAsDokuwiki) {
                                    archivePath = Sharing.exportDW(p);
                                } else {
                                    archivePath = Sharing.export(p);
                                }
                                File archiveFile = new File(archivePath);
                                File output = new File(externalDestDir, archiveFile.getName());

                                // copy the exported archive to the output dir
                                FileUtils.copyFile(archiveFile, output);
                                archiveFile.delete();

                                AppContext.context().showToastMessage(String.format(getResources().getString(R.string.project_exported_to), output.getParentFile().getAbsolutePath()), Toast.LENGTH_SHORT);
                            } catch (IOException e) {
                                AppContext.context().showException(e);
                            }
                        } else {
                            AppContext.context().showToastMessage(R.string.missing_external_storage);
                        }
                    }

                    @Override
                    public void onPostExecute() {
                        dialog.dismiss();
                    }
                };
                thread.start();
            }
        }, externalMediaAvailable, exportToSDMessage));

        mSharingTools.add(new ToolItem(getResources().getString(R.string.import_from_sd), "", R.drawable.ic_icon_import_sd, new ToolItem.ToolAction() {
            @Override
            public void run() {
                if(AppContext.isExternalMediaAvailable()) {
                    // write files to the removeable sd card
                    File path = AppContext.getPublicDownloadsDirectory();
//                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT); // native file browser
                    Intent intent = new Intent(SharingActivity.this, FileBrowserActivity.class);
                    intent.setDataAndType(Uri.fromFile(path), "file/*");
                    startActivityForResult(intent, IMPORT_PROJECT_FROM_SD_REQUEST);
                } else {
                    Logger.w(SharingActivity.class.getName(), "The external storage could not be found");
                }
            }
        }, AppContext.isExternalMediaAvailable(), getResources().getString(R.string.missing_external_storage)));

        // p2p sharing requires an active network connection.
        // TODO: Later we may need to adjust this since bluetooth and other services do not require an actual network.
        boolean isNetworkAvailable = app().isNetworkAvailable();

        // TODO: we should check to see if the user has any sharable content first.
        mSharingTools.add(new ToolItem(getResources().getString(R.string.export_to_device), "", R.drawable.ic_icon_export_nearby, new ToolItem.ToolAction() {
            @Override
            public void run() {
                Intent intent = new Intent(SharingActivity.this, DeviceToDeviceActivity.class);
                Bundle extras = new Bundle();
                extras.putBoolean("startAsServer", true);
                intent.putExtras(extras);
                startActivity(intent);
            }
        }, isNetworkAvailable, getResources().getString(R.string.internet_not_available)));

        mSharingTools.add(new ToolItem(getResources().getString(R.string.import_from_device), "", R.drawable.ic_icon_import_nearby, new ToolItem.ToolAction() {
            @Override
            public void run() {
                Intent intent = new Intent(SharingActivity.this, DeviceToDeviceActivity.class);
                Bundle extras = new Bundle();
                extras.putBoolean("startAsServer", false);
                intent.putExtras(extras);
                startActivity(intent);
            }
        }, isNetworkAvailable, getResources().getString(R.string.internet_not_available)));

        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == IMPORT_PROJECT_FROM_SD_REQUEST) {
            if(data != null) {
                File file = new File(data.getData().getPath());
                importTranslation(file);
            }
        }
    }

    /**
     * Detects the format of the file and imports it
     * @param file
     */
    public void importTranslation(final File file) {
        try {
            if (file.exists() && file.isFile()) {
                String[] name = file.getName().split("\\.");
                if (name[name.length - 1].toLowerCase().equals(Project.PROJECT_EXTENSION)) {
                    // import translationStudio project
                    final ProgressDialog dialog = new ProgressDialog(this);
                    dialog.setMessage(getResources().getString(R.string.import_project));
                    dialog.setCancelable(false);
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.show();

                    final Handler handle = new Handler(Looper.getMainLooper());
                    new ThreadableUI(this) {

                        @Override
                        public void onStop() {

                        }

                        @Override
                        public void run() {
                            ProjectImport[] importRequests = Sharing.prepareArchiveImport(file);
                            if (importRequests.length > 0) {
                                boolean importWarnings = false;
                                for (ProjectImport s : importRequests) {
                                    if (!s.isApproved()) {
                                        importWarnings = true;
                                    }
                                }
                                if (importWarnings) {
                                    // review the import status in a dialog
                                    FragmentTransaction ft = getFragmentManager().beginTransaction();
                                    Fragment prev = getFragmentManager().findFragmentByTag("dialog");
                                    if (prev != null) {
                                        ft.remove(prev);
                                    }
                                    ft.addToBackStack(null);
                                    app().closeToastMessage();
                                    ProjectTranslationImportApprovalDialog newFragment = new ProjectTranslationImportApprovalDialog();
                                    newFragment.setImportRequests(importRequests);
                                    newFragment.setOnClickListener(new ProjectTranslationImportApprovalDialog.OnClickListener() {
                                        @Override
                                        public void onOk(ProjectImport[] requests) {
                                            handle.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    dialog.setMessage(getResources().getString(R.string.loading));
                                                    dialog.show();
                                                }
                                            });

                                            for (ProjectImport r : requests) {
                                                Sharing.importProject(r);
                                            }
                                            Sharing.cleanImport(requests);
                                            AppContext.context().showToastMessage(R.string.success);
                                            handle.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    dialog.dismiss();
                                                }
                                            });
                                        }

                                        @Override
                                        public void onCancel(ProjectImport[] requests) {

                                        }
                                    });
                                    newFragment.show(ft, "dialog");
                                } else {
                                    // TODO: we should update the status with the results of the import and let the user see an overview of the import process.
                                    for (ProjectImport r : importRequests) {
                                        Sharing.importProject(r);
                                    }
                                    Sharing.cleanImport(importRequests);
                                    app().showToastMessage(R.string.success);
                                }
                            } else {
                                Sharing.cleanImport(importRequests);
                                app().showToastMessage(R.string.translation_import_failed);
                            }
                        }

                        @Override
                        public void onPostExecute() {
                            dialog.dismiss();
                        }
                    }.start();
                } else if (name[name.length - 1].toLowerCase().equals("zip")) {
                    // import DokuWiki files
                    final ProgressDialog dialog = new ProgressDialog(SharingActivity.this);
                    dialog.setMessage(getResources().getString(R.string.import_project));
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.setCancelable(false);
                    dialog.show();
                    new ThreadableUI(this) {

                        @Override
                        public void onStop() {

                        }

                        @Override
                        public void run() {
                            if (Sharing.importDokuWikiArchive(file)) {
                                app().showToastMessage(R.string.success);
                            } else {
                                app().showToastMessage(R.string.translation_import_failed);
                            }
                        }

                        @Override
                        public void onPostExecute() {
                            dialog.dismiss();
                        }
                    }.start();
                } else if (name[name.length - 1].toLowerCase().equals("txt")) {
                    // import legacy 1.x DokuWiki files

                    final ProgressDialog dialog = new ProgressDialog(SharingActivity.this);
                    dialog.setMessage(getResources().getString(R.string.import_project));
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.setCancelable(false);
                    dialog.show();
                    new ThreadableUI(this) {

                        @Override
                        public void onStop() {

                        }

                        @Override
                        public void run() {
                            if (Sharing.importDokuWiki(file)) {
                                app().showToastMessage(R.string.success);
                            } else {
                                app().showToastMessage(R.string.translation_import_failed);
                            }
                        }

                        @Override
                        public void onPostExecute() {
                            dialog.dismiss();
                        }
                    }.start();
                }
            } else {
                app().showToastMessage(R.string.missing_file);
            }
        } catch(Exception e) {
            Logger.e(this.getClass().getName(), "Failed to read file", e);
        }
    }
}
