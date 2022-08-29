public class InstanceMethod {
  public boolean bar(boolean b, int ii) {
    return ii == 0;
  }

  public void test() {
    bar(true, 1)
  }
}