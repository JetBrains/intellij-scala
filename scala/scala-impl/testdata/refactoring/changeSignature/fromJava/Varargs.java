public class Varargs {
    public void <caret>foo(int i, String... strs) {}

    public void test() {
        foo(1);
        foo(1, "");
        foo(1, "", "1");
    }
}