public class VarargsRemove {
    public void foo(int i, boolean b) {}

    public void test() {
        foo(1, true);
        foo(1, true);
        foo(1, true);
    }
}