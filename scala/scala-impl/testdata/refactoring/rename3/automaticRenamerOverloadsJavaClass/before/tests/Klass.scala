package tests

class Sub extends JavaSuper {
  override def foo(a: Int): Unit = {
  }

  override def foo(): Unit = {
  }
}

@main def main(): Unit = {
  new JavaSuper().foo()
  JavaSuper().foo(1)
  Sub().foo()
  new Sub().foo(1)
}
