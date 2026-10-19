package nfc.wallet.compat;

/** Restores the prior in-memory service state if a setter fails after mutating it. */
final class BindingTransaction {
    interface Editor {
        void writeGroup(Object group) throws Throwable;
        void removeGroup() throws Throwable;
        void bindEse() throws Throwable;
        void restoreElement(String element) throws Throwable;
    }

    static void apply(Editor editor, Object previousGroup, String previousElement,
                      Object nextGroup, boolean changeGroup, boolean changeElement) throws Throwable {
        boolean groupAttempted = false;
        boolean elementAttempted = false;
        try {
            if (changeGroup) {
                groupAttempted = true;
                editor.writeGroup(nextGroup);
            }
            if (changeElement) {
                elementAttempted = true;
                editor.bindEse();
            }
        } catch (Throwable failure) {
            if (elementAttempted) {
                try { editor.restoreElement(previousElement); }
                catch (Throwable recovery) { if (recovery != failure) failure.addSuppressed(recovery); }
            }
            if (groupAttempted) {
                try {
                    if (previousGroup == null) editor.removeGroup();
                    else editor.writeGroup(previousGroup);
                } catch (Throwable recovery) { if (recovery != failure) failure.addSuppressed(recovery); }
            }
            throw failure;
        }
    }
}
