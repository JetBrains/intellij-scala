class Foo {
  /*ref*/foo
}

object Foo {
  def foo(): Unit = {}
}

/*
import Foo.foo

class Foo {
  foo
}

object Foo {
  def foo(): Unit = {}
}
 */