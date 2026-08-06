package tests;

public class Foo {
    public static void main(String[] args) {
        NameAfterRename.x();
        NameAfterRename$.MODULE$.x();

        NameAfterRename b1 = new NameAfterRename();
        b1.baz();

        NameAfterRename b2 = new NameAfterRename(0);
        b2.baz();
    }
}
