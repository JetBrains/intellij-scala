package tests

object NameAfterRename {
  val x = 0

  def qqq() = {}

  def apply() = {}

  def unapply(i: Int) = Some(i)
}

trait NameAfterRename {
  def baz(): Int = 1
}

object Test {
  def main(args: Array[String]) {
    NameAfterRename()
    1 match {
      case NameAfterRename(i) =>
    }
  }
}
