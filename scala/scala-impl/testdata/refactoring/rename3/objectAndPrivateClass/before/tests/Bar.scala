package tests

object /*caret*/Bar {
  val x = 0
  def apply() = {}
  def unapply(i: Int) = Some(i)
}

private class Bar/*caret*/ {
  def this/*caret*/(i: Int) = this()
  def baz() = {}
}

object Test {
  def main(args: Array[String]) {
    /*caret*/Bar()
    1 match {
      case Bar/*caret*/(i) =>
    }
  }
}
