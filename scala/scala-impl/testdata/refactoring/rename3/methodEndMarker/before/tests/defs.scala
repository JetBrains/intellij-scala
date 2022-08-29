package tests

def /*caret*/bar() =
  println()
  1
end bar

def test(): Unit =
  println(/*caret*/bar())
