package tests

object Foo:
  val x =
    1
  end x
  object Bar:
    val /*caret*/x =
      2
    end x
  end Bar
end Foo

def test(): Unit =
  println(Foo.Bar./*caret*/x)
