public class SimpleMethodJava extends SimpleMethodScala {
    @Override
    public void foo(int i, String s, boolean b) {
        super.foo(i, s, true);
    }

    public void test() {
        foo(1, "", true);
    }
}