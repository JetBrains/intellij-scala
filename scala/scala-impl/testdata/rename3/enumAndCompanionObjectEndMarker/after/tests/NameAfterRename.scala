package tests

enum NameAfterRename:
  case A, B
  case C(i: Int)
end NameAfterRename

object NameAfterRename:
  def apply() = NameAfterRename.A
end NameAfterRename

object Test:
  val foo = NameAfterRename()
end Test
