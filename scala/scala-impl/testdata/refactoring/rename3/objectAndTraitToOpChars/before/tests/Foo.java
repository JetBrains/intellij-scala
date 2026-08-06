package tests;

public class Foo {
    public static void main(String[] args) {
        Bar/*caret*/$.MODULE$.x();

        Class c = Bar$class/*caret*/.class;

        Bar/*caret*/ b = new /*caret*/Bar();
        b.baz();
    }
}
