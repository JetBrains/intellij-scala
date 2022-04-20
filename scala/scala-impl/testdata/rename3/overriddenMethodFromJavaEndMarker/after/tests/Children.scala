package tests

object Child1 extends Base:
  override val NameAfterRename =
    1
  end NameAfterRename

  val x = new Base {
    override def NameAfterRename() =
      3
    end NameAfterRename
  }
  x.NameAfterRename()
end Child1

abstract class ChildEarlyInit extends Base {
  override def NameAfterRename =
    1
  end NameAfterRename
}

class Child2 extends ChildEarlyInit {
  NameAfterRename
}

class Child3 extends Base:
  override def NameAfterRename() =
    2
  end NameAfterRename

  NameAfterRename()

  Child1.NameAfterRename
end Child3
