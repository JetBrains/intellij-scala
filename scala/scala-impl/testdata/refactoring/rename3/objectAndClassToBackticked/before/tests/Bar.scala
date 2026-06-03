package tests

object /*caret*/Bar {
  val x = 0

  def qqq() = {}

  def apply() = {}

  def unapply(i: Int) = Some(i)
}

class Bar/*caret*/ {
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
