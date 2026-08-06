package foo.bar

sealed abstract class /*caret*/RenameMe
object /*caret*/RenameMe {
  case object ChildA extends RenameMe
  case object ChildB extends RenameMe
}
