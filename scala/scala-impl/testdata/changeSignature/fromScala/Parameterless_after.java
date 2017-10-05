public class ParameterlessJava extends Parameterless {
    @Override
    public int bar(int i) {
        return 0;
    }

    public void test() {
        bar(bar$default$1());
    }
}