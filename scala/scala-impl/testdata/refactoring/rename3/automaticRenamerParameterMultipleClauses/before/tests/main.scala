package tests

trait Interface {
  def foo(a: Int)(b: String, c: Double)(d: Boolean): Unit = {
  }
}

class Super {
  def foo(a: Int)(b: String, c: Double)(d: Boolean): Unit = {
  }
}

class Middle extends Super with Interface {
  override def foo(a: Int)(b: String, c/*caret*/: Double)(d: Boolean): Unit = {
  }
}

class Sub extends Middle {
  override def foo(a: Int)(b: String, c: Double)(d: Boolean): Unit = {
  }
}
