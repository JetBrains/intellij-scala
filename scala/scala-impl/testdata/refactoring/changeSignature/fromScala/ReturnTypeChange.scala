class ReturnType {
  def <caret>foo() = {
    1
  }
}

class ReturnTypeChild extends ReturnType {
  override def foo(): Int = super.foo()
}