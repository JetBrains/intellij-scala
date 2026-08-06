package tests

object +++ {
  val x = 0

  def qqq() = {}

  def apply() = {}

  def unapply(i: Int) = Some(i)
}

class +++ {
  def baz() = {}
}

object Test {
  def main(args: Array[String]) {
    +++()
    1 match {
      case +++(i) =>
    }
  }
}
