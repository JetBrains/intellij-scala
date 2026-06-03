import java.lang.Override;

public class OverriderInAnonClass {
    public int <caret>foo(int i) {
        return i;
    }

    public void test() {
        foo(1);
    }

    static class Inner extends OverriderInAnonClass {
        @Override
        public int foo(int i) {
            return super.foo(i);
        }
    }
}