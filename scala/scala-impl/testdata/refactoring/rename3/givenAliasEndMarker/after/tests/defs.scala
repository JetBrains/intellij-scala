package tests

given NameAfterRename(using i: Int): String =
  "*" * i
end NameAfterRename

def test(): Unit =
  println(NameAfterRename(42))
