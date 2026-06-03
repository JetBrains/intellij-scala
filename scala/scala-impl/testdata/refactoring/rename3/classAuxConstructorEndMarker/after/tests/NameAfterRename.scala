package tests

class NameAfterRename(i: Int):
  def bar(x: Int) = i + x
  def this() =
    this(42)
  end this
  def this(str: String) =
    this(str.length)
  end this
end NameAfterRename

object Test:
  val foo1 = NameAfterRename(1)
  val foo2 = NameAfterRename()
  val foo3 = NameAfterRename("foo")
end Test
