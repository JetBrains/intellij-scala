public class SimpleMethodJava extends SimpleMethodScala {
  @Override
  public void bar(int ii, boolean b) {
    super.bar(ii, true);
  }

  public void test() {
    bar(1, true);
  }
}