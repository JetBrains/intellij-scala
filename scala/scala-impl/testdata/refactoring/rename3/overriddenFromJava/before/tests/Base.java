package tests;

public class Base {
    public int /*caret*/foo() {
        return 0;
    }

    public void test() {
        foo/*caret*/();
    }
}