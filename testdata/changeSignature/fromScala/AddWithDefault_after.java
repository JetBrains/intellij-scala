public class SimpleMethodJava extends SimpleMethodScala {
  @Override
  public void foo(int i, Object s, boolean b) {
    super.foo(i, foo$default$2(), true);
  }

  public void test() {
    foo(1, foo$default$2(), true);
  }
}