package tests;

public class Foo {
    public static void main(String[] args) {
        NameAfterRename.x();
        NameAfterRename$.MODULE$.x();

        NameAfterRename b = new NameAfterRename();
        b.baz();
    }
}
