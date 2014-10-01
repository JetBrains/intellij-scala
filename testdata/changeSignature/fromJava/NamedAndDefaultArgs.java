public class NamedAndDefaultArgs {
  public void <caret>foo(int i, int j, String s, boolean b) {}

  static class Inner extends NamedAndDefaultArgs {
    @Override
    public void foo(int i, int j, String s, boolean b) {
      super.foo(i, j, s, b);
    }
  }
}