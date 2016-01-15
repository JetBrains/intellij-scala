public class SimpleMethodJava extends SimpleMethodScala {
    @Override
    public void foo(int i, Object s, boolean b) {
        super.foo(i, "hi", true);
    }

    public void test() {
        foo(1, "hi", true);
    }
}