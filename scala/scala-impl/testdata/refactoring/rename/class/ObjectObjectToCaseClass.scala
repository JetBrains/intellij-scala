object O {
  case class Test1(a: Int)
  object Test1
  Test1(2)
  /*caret*/Test1.apply(1)
}
/*
object O {
  case class NameAfterRename(a: Int)
  object NameAfterRename
  NameAfterRename(2)
  /*caret*/NameAfterRename.apply(1)
}
*/