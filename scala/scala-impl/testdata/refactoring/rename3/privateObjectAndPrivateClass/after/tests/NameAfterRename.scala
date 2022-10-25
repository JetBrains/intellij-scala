package tests

private object NameAfterRename {
  val x = 0
  def apply() = {}
  def unapply(i: Int) = Some(i)
}

private class NameAfterRename {
  def this(i: Int) = this()
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
