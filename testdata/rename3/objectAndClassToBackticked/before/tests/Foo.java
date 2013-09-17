package tests;

public class Foo {
    public static void main(String[] args) {
        Ba/*caret*/r.x();
        B/*caret*/ar$.MODULE$.x();

        Bar/*caret*/ b = new /*caret*/Bar();
        b.baz();
    }
}
