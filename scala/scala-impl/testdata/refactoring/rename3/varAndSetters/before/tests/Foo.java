package tests;

public class Foo {
    public static void main(String[] args) {
        int b1 = Baz.bar/*caret*/();
        int b2 = Baz$.MODULE$.bar/*caret*/();
        Baz.bar_$eq/*caret*/(2);
        Baz$.MODULE$.bar_$eq/*caret*/(3);
    }
}