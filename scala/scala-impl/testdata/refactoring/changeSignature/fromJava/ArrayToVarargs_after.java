public class ArrayToVarargs {
    public void foo(int i, boolean b, int... js) {}

    public void test() {
        foo(1, true, 2, 2);
        int[] xs = new int[]{3, 3};
        foo(1, true, xs);
    }
}