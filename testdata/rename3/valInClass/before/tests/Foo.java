package tests;

public class Foo {
    public static void main(String[] args) {
        int b1 = Bar.bar/*caret*/();
        int b2 = Bar$.MODULE$./*caret*/bar();
    }
}

