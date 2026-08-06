package tests;

public class Foo {
    public static void main(String[] args) {
        int b2 = Baz2.NameAfterRename();
        int b3 = Baz3.NameAfterRename();
        (new Baz()).NameAfterRename();
    }

    public static class JavaBaz extends Baz {
        @Override
        public int NameAfterRename() {
            return 4;
        }
    }
}