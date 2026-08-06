package tests

object `a` {
  val x = 0

  def qqq() = {}

  def apply() = {}

  def unapply(i: Int) = Some(i)
}

class `a` {
  def baz() = {}
}

object Test {
  def main(args: Array[String]) {
    `a`()
    1 match {
      case `a`(i) =>
    }
  }
}
