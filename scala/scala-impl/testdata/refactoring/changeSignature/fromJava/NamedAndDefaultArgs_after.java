public class NamedAndDefaultArgs {
    public void foo(String s, boolean b, boolean b2) {}

    static class Inner extends NamedAndDefaultArgs {
        @Override
        public void foo(String s, boolean b, boolean b2) {
            System.out.println();
        }

        public void boo() {
            super.foo("3", false, true)
        }
    }
}