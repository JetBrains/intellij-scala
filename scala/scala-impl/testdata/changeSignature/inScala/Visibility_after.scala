class Visibility {
  protected def foo(i: Int): Int = 1
}

class VisibilityTest extends Visibility {
  protected override def foo(i: Int): Int = 2
}