public class ReturnTypeTest extends ReturnType {
    @Override
    public void foo() {
        return super.foo();
    }
}