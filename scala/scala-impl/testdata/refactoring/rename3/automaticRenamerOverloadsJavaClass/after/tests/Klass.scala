package tests

class Sub extends JavaSuper {
  override def bar(a: Int): Unit = {
  }

  override def bar(): Unit = {
  }
}

@main def main(): Unit = {
  new JavaSuper().bar()
  JavaSuper().bar(1)
  Sub().bar()
  new Sub().bar(1)
}
