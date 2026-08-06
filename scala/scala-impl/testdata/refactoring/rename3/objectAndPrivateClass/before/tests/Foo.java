package tests;

public class Foo {
    public static void main(String[] args) {
        Bar/*caret*/.x();
        Bar$/*caret*/.MODULE$.x();

        Bar/*caret*/ b1 = new /*caret*/Bar();
        b1.baz();

        Bar/*caret*/ b2 = new /*caret*/Bar(0);
        b2.baz();
    }
}
