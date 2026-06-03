object A {
  val a = 1
  val (_, /*caret*/`a`) = (a, a)
}
/*
object A {
  val NameAfterRename = 1
  val (_, /*caret*/NameAfterRename) = (NameAfterRename, NameAfterRename)
}
*/