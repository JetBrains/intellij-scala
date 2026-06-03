class ReturnType {
  def foo(): Unit = {
    1
  }
}

class ReturnTypeChild extends ReturnType {
  override def foo(): Unit = super.foo()
}