object Koo {
  def foo(NameAfterRename: String): Unit = {
    println(NameAfterRename)
  }

  foo(NameAfterRename = "")
}
