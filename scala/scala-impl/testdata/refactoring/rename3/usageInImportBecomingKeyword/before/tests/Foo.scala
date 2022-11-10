package tests

import tests.Foo./*caret*/bar

object Foo {
  val ba/*caret*/r: Int = 1
}
