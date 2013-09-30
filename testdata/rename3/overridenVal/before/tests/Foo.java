package tests;

public class Foo {
    public static void main(String[] args) {
        int b2 = Baz2.baz/*caret*/();
        int b3 = Baz3.baz/*caret*/();
        (new Baz()).baz();
    }
}