public class InstanceMethod {
  public boolean <caret>foo(int i) {
    return i == 0;
  }

  public void test() {
    foo(1)
  }
}