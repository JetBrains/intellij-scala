package foo.bar

sealed abstract class NameAfterRename
object NameAfterRename {
  case object ChildA extends NameAfterRename
  case object ChildB extends NameAfterRename
}
