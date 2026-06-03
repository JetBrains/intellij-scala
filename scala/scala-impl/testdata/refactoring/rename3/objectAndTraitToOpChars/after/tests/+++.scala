package tests

object +++ {
  val x = 0

  def qqq() = {}

  def apply() = {}

  def unapply(i: Int) = Some(i)
}

trait +++ {
  def baz(): Int = 1
}

object Test {
  def main(args: Array[String]) {
    +++()
    1 match {
      case +++(i) =>
    }
  }
}
