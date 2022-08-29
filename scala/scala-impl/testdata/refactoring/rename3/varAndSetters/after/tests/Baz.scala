package tests

object Baz {
  var NameAfterRename = 0
}

object Test {
  def foo() = {
    Baz.NameAfterRename = 1
    Baz.NameAfterRename_=(2)
    Baz.NameAfterRename_$eq(3)
    Baz.NameAfterRename.toString
  }
}