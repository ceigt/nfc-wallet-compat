package nfc.wallet.compat;

public final class BindingTest {
    static class Editor implements BindingTransaction.Editor {
        Object group;
        String element;
        boolean failGroup, failBind, failRestore;
        int calls;
        public void writeGroup(Object group) {
            this.group = group; calls++;
            if (failGroup) { failGroup = false; throw new IllegalStateException("group write failed after mutation"); }
        }
        public void removeGroup() { group = null; calls++; }
        public void bindEse() {
            element = "eSE1"; calls++;
            if (failBind) throw new IllegalStateException("SE write failed after mutation");
        }
        public void restoreElement(String element) {
            calls++;
            if (failRestore) throw new IllegalStateException("SE rollback failed");
            this.element = element;
        }
    }
    public static void main(String[] args) throws Throwable {
        Object original = new Object(), replacement = new Object();
        Editor editor = new Editor(); editor.group = original;
        BindingTransaction.apply(editor, original, null, replacement, true, true);
        assert editor.group == replacement && "eSE1".equals(editor.element);
        int calls = editor.calls;
        BindingTransaction.apply(editor, replacement, "eSE1", null, false, false);
        assert editor.calls == calls;
        for (int stage = 0; stage < 3; stage++) {
            editor = new Editor(); editor.group = stage == 2 ? null : original;
            editor.failGroup = stage == 0; editor.failBind = stage != 0;
            Object previous = editor.group;
            try {
                BindingTransaction.apply(editor, previous, null, replacement, true, true);
                throw new AssertionError("Injected failure swallowed");
            } catch (IllegalStateException expected) {}
            assert editor.group == previous && editor.element == null;
        }
        editor = new Editor(); editor.group = original; editor.failBind = true; editor.failRestore = true;
        try {
            BindingTransaction.apply(editor, original, null, replacement, true, true);
            throw new AssertionError("Rollback failure swallowed");
        } catch (IllegalStateException expected) { assert expected.getSuppressed().length == 1; }
        assert editor.group == original;
        System.out.println("PASS: binding success/no-op; mutation-then-failure rollback with existing/absent groups; rollback errors preserved");
    }
}
