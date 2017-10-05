public class Varargs {
    public void foo(int i, boolean b, String... strs) {}

    public void test() {
        foo(1, true);
        foo(1, true, "");
        foo(1, true, "", "1");
    }
}