package tests

object /*caret*/Bar {
  val x = 0

  def qqq() = {}

  def apply() = {}

  def unapply(i: Int) = Some(i)
}

trait Bar/*caret*/ {
  def baz(): Int = 1
}

object Test {
  def main(args: Array[String]) {
    Bar/*caret*/()
    1 match {
      case /*caret*/Bar(i) =>
    }
  }
}
