public class NamedAndDefaultArgs {
  public void foo(String s, boolean b, boolean b2) {}

  static class Inner extends NamedAndDefaultArgs {
    @Override
    public void foo(String s, boolean b, boolean b2) {
      super.foo(s, b, b2);
    }
  }
}