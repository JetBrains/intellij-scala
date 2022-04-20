package tests

trait NameAfterRename:
  def bar: Int = 42
end NameAfterRename

object NameAfterRename:
  def apply() = new NameAfterRename() {}
end NameAfterRename

class Bar extends NameAfterRename:
  val baz = "..."
end Bar

object Test:
  val foo = NameAfterRename()
  val bar = new Bar()
end Test
