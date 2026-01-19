package tests

def foo/*caret*/(): Unit = {
}

def foo/*caret*/(a: Int): Unit = {
}

extension (s: String)
  def foo/*caret*/(): Unit = {
  }

@main def main(): Unit = {
  foo()
  foo(1)
  "".foo()
}
