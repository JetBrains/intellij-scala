object ObjectToCaseClass {
  case class Test1(a: Int)
  Test1(2)
  /*caret*/Test1.apply(1)
}
/*
object ObjectToCaseClass {
  case class NameAfterRename(a: Int)
  NameAfterRename(2)
  /*caret*/NameAfterRename.apply(1)
}
*/