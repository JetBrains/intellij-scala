class Foo {
  /*ref*/foo
}

object Foo {
  private[this] def foo(): Unit = {}

  private def bar(): Unit = {}
}

/*
class Foo {
  foo
}

object Foo {
  private[this] def foo(): Unit = {}

  private def bar(): Unit = {}
}
 */