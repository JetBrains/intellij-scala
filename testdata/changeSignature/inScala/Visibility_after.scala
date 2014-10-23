class Visibility {
  protected def foo(i: Int) = 1
}

class VisibilityTest extends Visibility {
  protected override def foo(i: Int) = 2
}