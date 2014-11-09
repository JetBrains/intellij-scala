public class StaticMethod {
  public static int <caret>foo(int i) {
    return i;
  }

  public static void test() {
    foo(1)
  }
}