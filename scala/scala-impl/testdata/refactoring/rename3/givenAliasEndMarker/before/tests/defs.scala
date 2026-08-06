package tests

given /*caret*/someGivenString(using i: Int): String =
  "*" * i
end someGivenString

def test(): Unit =
  println(/*caret*/someGivenString(42))
