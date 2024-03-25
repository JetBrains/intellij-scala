package tests

package inner:
  val somethingElse = 42
end /*caret*/inner

def test1(): Unit =
  println(inner.foo)
