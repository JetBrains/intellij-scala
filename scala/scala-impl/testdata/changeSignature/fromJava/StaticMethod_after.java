public class StaticMethod {
  public static int bar(int ii, boolean b) {
    return ii;
  }

  public static void test() {
    bar(1, true)
  }
}