package com.door43.translationstudio.tasks;

import com.door43.util.threads.ManagedTask;

/**
 * Created by joel on 3/9/2015.
 */
public class DownloadLanguagesCatalogTask extends ManagedTask {
    private OnProgressListener mListener;

    @Override
    public void start() {
        // TODO: begin executing code
        if(mListener != null) {
            mListener.onProgress(1, "downloading languages catalog");
        }
    }

    /**
     * Sets the listener to be called on progress updates.
     * @param listener
     */
    public void setProgressListener(OnProgressListener listener) {
        mListener = listener;
    }


    public static interface OnProgressListener {
        public void onProgress(double progress, String message);
    }
}
