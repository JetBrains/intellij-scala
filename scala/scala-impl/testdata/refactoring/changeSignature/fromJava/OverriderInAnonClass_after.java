import java.lang.Override;

public class OverriderInAnonClass {
    public boolean bar(boolean b, int ii) {
        return ii;
    }

    public void test() {
        bar(true, 1);
    }

    static class Inner extends OverriderInAnonClass {
        @Override
        public boolean bar(boolean b, int ii) {
            return super.bar(b, ii);
        }
    }
}