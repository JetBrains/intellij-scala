package tests

object NameAfterRename:
  def bar: Int = 42
end NameAfterRename

object Test:
  val foo = NameAfterRename
  val bar = foo.bar
end Test
