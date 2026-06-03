package tests

package inner/*caret*/:
  val foo = 42
end inner

def test(): Unit =
  println(/*caret*/inner.foo)
