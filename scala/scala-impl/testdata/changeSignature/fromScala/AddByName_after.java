import scala.Function0;

public class ByNameJava extends ByName {
    @Override
    public void foo(int x, Function0<Object> s) {
        super.foo(x, s);
    }
}