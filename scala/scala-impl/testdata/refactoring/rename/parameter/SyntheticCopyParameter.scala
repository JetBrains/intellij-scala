object TestAsdf {

  case class Foo(sdf: Int)

  def useFoo(foo: Foo) = {
    foo.copy(/*caret*/sdf = 2)
    foo.sdf
  }
}
/*
object TestAsdf {

  case class Foo(NameAfterRename: Int)

  def useFoo(foo: Foo) = {
    foo.copy(/*caret*/NameAfterRename = 2)
    foo.NameAfterRename
  }
}
*/