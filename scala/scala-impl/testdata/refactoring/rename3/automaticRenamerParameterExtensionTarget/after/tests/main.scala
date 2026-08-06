package tests

trait Interface {
  extension (i: Int) def foo(a: Int, b: String): Unit = {}
}

class Super {
  extension (i: Int) def foo(a: Int, b: String): Unit = {}
}

class Middle extends Super with Interface {
  extension (receiver: Int) override def foo(a: Int, b: String): Unit = {}
}

class Sub extends Middle {
  extension (receiver: Int) override def foo(a: Int, b: String): Unit = {}
}
