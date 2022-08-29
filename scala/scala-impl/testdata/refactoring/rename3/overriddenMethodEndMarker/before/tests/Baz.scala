package tests

class Baz:
  def baz/*caret*/() =
    1
  end baz
end Baz

object Baz2 extends Baz:
  override val baz/*caret*/ =
    2
  end baz
end Baz2

abstract class BazEarlyInit(override val /*caret*/baz: Int) extends Baz

object Baz3 extends BazEarlyInit(3)

class Baz4 extends Baz:
  override def baz/*caret*/() =
    4
  end baz
end Baz4

object Test:
  def foo(): Unit =
    class Baz5 extends Baz:
      override def baz/*caret*/(): Int =
        5
      end baz
    end Baz5
  end foo

  Baz2.baz/*caret*/
  Baz3.baz/*caret*/
end Test
