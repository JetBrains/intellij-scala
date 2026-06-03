package tests

trait Interface {
  def foo(a: Int, b: String): Unit = {
  }
}

class Super {
  def foo(a: Int, b: String): Unit = {
  }
}

class Middle extends Super with Interface {
  override def foo(aa: Int, b: String): Unit = {
  }
}

class Sub extends Middle {
  override def foo(aa: Int, b: String): Unit = {
  }
}
