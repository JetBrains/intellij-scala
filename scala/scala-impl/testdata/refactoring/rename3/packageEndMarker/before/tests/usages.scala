package tests

package inner:
  val somethingElse = 42
end inner

def test1(): Unit =
  println(/*caret*/inner.foo)
