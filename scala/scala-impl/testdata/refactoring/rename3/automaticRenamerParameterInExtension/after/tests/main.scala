package tests

trait Interface {
  extension (i: Int) def foo(a: Int, b: String): Unit = {}
}

class Super {
  extension (i: Int) def foo(a: Int, b: String): Unit = {}
}

class Middle extends Super with Interface {
  extension (i: Int) override def foo(aa: Int, b: String): Unit = {}
}

class Sub extends Middle {
  extension (i: Int) override def foo(aa: Int, b: String): Unit = {}
}
