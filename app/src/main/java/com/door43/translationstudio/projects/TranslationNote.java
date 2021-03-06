package com.door43.translationstudio.projects;

import java.util.ArrayList;
import java.util.List;

/**
 * A set of translation notes for a frame
 *
 */
public class TranslationNote {
    private List<Note> mNotes = new ArrayList<Note>();

    public TranslationNote(List<Note> notes) {
        mNotes = notes;
    }

    /**
     * Returns a list of notes
     * @return
     */
    public List<Note> getNotes() {
        return mNotes;
    }

    /**
     * stores an individual note
     */
    public static class Note {
        private String mRef;
        private String mText;

        public Note(String ref, String text) {
            mRef = ref;
            mText = text;
        }

        public String getRef() {
            return mRef;
        }

        public String getText() {
            return mText;
        }
    }
}
