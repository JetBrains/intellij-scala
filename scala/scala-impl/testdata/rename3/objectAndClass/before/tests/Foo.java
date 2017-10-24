package tests;

public class Foo {
    public static void main(String[] args) {
        Bar/*caret*/.x();
        Bar$/*caret*/.MODULE$.x();

        Bar/*caret*/ b = new /*caret*/Bar();
        b.baz();
    }
}
