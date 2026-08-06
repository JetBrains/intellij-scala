public class NamedAndDefaultArgs {
    public void <caret>foo(int i, int j, String s, boolean b) {}

    static class Inner extends NamedAndDefaultArgs {
        @Override
        public void foo(int i, int j, String s, boolean b) {
            System.out.println();
        }

        public void boo() {
            super.foo(1, 2, "3", false)
        }
    }
}