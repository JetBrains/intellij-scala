package tests

object Foo:
  val list = List(1, 2, 3)
  val (nameAfterRename, tail) =
    (list.head, list.tail)
  end val
end Foo

def test(): Unit =
  println(Foo.nameAfterRename)
