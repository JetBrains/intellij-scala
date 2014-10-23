class Visibility {
  def <caret>foo() = 1
}

class VisibilityTest extends Visibility {
  override val foo = 2
}