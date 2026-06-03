package tests

object Foo:
  val x =
    1
  end x
  object Bar:
    val NameAfterRename =
      2
    end NameAfterRename
  end Bar
end Foo

def test(): Unit =
  println(Foo.Bar.NameAfterRename)
