package tests;

public class Foo {
    public static void main(String[] args) {
        int b1 = BazInst.NameAfterRename();
        int b2 = BazInst$.MODULE$.NameAfterRename();
        new BazClass().NameAfterRename();
    }
}