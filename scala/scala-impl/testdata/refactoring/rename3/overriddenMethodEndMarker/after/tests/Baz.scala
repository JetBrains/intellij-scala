package tests

class Baz:
  def NameAfterRename() =
    1
  end NameAfterRename
end Baz

object Baz2 extends Baz:
  override val NameAfterRename =
    2
  end NameAfterRename
end Baz2

abstract class BazEarlyInit(override val NameAfterRename: Int) extends Baz

object Baz3 extends BazEarlyInit(3)

class Baz4 extends Baz:
  override def NameAfterRename() =
    4
  end NameAfterRename
end Baz4

object Test:
  def foo(): Unit =
    class Baz5 extends Baz:
      override def NameAfterRename(): Int =
        5
      end NameAfterRename
    end Baz5
  end foo

  Baz2.NameAfterRename
  Baz3.NameAfterRename
end Test
