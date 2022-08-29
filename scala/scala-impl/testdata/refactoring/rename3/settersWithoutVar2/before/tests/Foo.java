package tests;

public class Foo {
    public static void main(String[] args) {
        int b1 = Bar.b/*caret*/ar();
        int b2 = Bar$.MODULE$.b/*caret*/ar();
        Bar$.MODULE$.bar_/*caret*/$eq(3);
        Bar2.bar_$/*caret*/eq(2);
        Bar2.ba/*caret*/r();
        Bar2$.MODULE$.b/*caret*/ar();
        Bar2$.MODULE$.bar_/*caret*/$eq(4);
    }
}
