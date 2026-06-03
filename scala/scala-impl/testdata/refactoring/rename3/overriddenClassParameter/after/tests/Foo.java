package tests;

public class Foo {
    public static void main(String[] args) {
        int b2 = Baz2.NameAfterRename();
        int b3 = Baz3.NameAfterRename();
        (new Baz(1)).NameAfterRename();
    }
}