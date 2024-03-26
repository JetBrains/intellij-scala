package tests

package /*caret*/inner:
  val somethingElse = 42
end inner

def test1(): Unit =
  println(inner.foo)
