trait Foo {
  /*ref*/foo
}

object Foo {
  val (_, foo) = ???
}

/*
import Foo.foo

trait Foo {
  foo
}

object Foo {
  val (_, foo) = ???
}
 */