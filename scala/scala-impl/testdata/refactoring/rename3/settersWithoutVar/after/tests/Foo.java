package tests;

public class Foo {
    public static void main(String[] args) {
        int b1 = Bar.NameAfterRename();
        int b2 = Bar$.MODULE$.NameAfterRename();
        Bar$.MODULE$.NameAfterRename_$eq(3);
        Bar2.NameAfterRename_$eq(2);
        Bar2.NameAfterRename();
        Bar2$.MODULE$.NameAfterRename();
        Bar2$.MODULE$.NameAfterRename_$eq(4);
    }
}
