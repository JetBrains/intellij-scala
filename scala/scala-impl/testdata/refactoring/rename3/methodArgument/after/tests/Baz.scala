package tests

object Baz {
  def baz(NameAfterRename: Int) = {
    1 match {
      case NameAfterRename => NameAfterRename
      case _ => 0
    }
  }

  baz(NameAfterRename = 2)
}
