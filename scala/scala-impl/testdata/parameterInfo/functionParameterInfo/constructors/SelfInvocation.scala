class SelfInvocation(x: Int, y: Int) {
  def this(s: String) {
    this(1, 2)
  }

  def this(x: Int) {
    this(<caret>)
  }

  def this(b: Boolean) {
    this("text")
  }
}
/*
s: String
x: Int, y: Int
*/