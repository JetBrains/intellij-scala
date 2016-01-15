package tests

object NameAfterRename {
  val x = 0

  def qqq() = {}

  def apply() = {}

  def unapply(i: Int) = Some(i)
}

class NameAfterRename {
  def baz() = {}
}

object Test {
  def main(args: Array[String]) {
    NameAfterRename()
    1 match {
      case NameAfterRename(i) =>
    }
  }
}
