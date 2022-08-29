public class ArrayToVarargs {
    public void <caret>foo(int i, int[] js, boolean b) {}

    public void test() {
        foo(1, new int[]{2, 2}, true);
        int[] xs = new int[]{3, 3};
        foo(1, xs, true);
    }
}