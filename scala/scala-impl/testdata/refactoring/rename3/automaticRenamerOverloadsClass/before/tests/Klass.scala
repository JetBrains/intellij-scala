package tests

class Klass {
  def foo/*caret*/(): Unit = {
    "".foo()
  }

  def foo/*caret*/(a: Int): Unit = {
  }

  extension (s: String) def foo/*caret*/(): Unit = {
  }

}

class Sub extends Klass {
  override def foo(a: Int): Unit = {
  }
}

@main def main(): Unit = {
  new Klass().foo()
  Klass().foo(1)

  // extension
  locally {
    val klass = new Klass()
    klass.foo("")()
    import klass.foo
    "".foo()
  }
}
