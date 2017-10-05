package tests;

public class Foo {
    public static void main(String[] args) {
        int b1 = Baz.NameAfterRename();
        int b2 = Baz$.MODULE$.NameAfterRename();
        Baz.NameAfterRename_$eq(2);
        Baz$.MODULE$.NameAfterRename_$eq(3);
    }
}