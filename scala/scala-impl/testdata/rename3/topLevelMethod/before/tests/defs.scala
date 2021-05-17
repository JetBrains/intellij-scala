package tests

def /*caret*/bar() = 1

def test(): Unit = {
  println(/*caret*/bar())
}