package tests;

public class Foo {
    public static void main(String[] args) {
        int b1 = Bar.bar();
        int b2 = Bar$.MODULE$.bar();
        Bar$.MODULE$.bar_/*caret*/$eq(3);
        Bar2.bar_$/*caret*/eq(2);
        Bar2.bar();
        Bar2$.MODULE$.bar();
        Bar2$.MODULE$.bar_/*caret*/$eq(4);
    }
}
