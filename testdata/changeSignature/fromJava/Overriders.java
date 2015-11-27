import java.lang.Override;

public class Overriders {
    public int <caret>foo(int i) {
        return i;
    }

    public void test() {
        foo(1);
    }

    static class Inner extends Overriders {
        @Override
        public int foo(int i) {
            return super.foo(i);
        }
    }
}