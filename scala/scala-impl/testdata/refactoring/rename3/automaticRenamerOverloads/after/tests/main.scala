package tests

def bar(): Unit = {
}

def bar(a: Int): Unit = {
}

extension (s: String)
  def bar(): Unit = {
  }

@main def main(): Unit = {
  bar()
  bar(1)
  "".bar()
}
