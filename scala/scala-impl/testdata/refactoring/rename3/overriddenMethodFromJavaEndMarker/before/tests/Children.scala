package tests

object Child1 extends Base:
  override val /*caret*/foo =
    1
  end foo

  val x = new Base {
    override def /*caret*/foo () =
      3
    end foo
  }
  x./*caret*/foo()
end Child1

abstract class ChildEarlyInit extends Base {
  override def /*caret*/foo =
    1
  end foo
}

class Child2 extends ChildEarlyInit {
  /*caret*/foo
}

class Child3 extends Base:
  override def /*caret*/foo() =
    2
  end foo

  /*caret*/foo()

  Child1./*caret*/foo
end Child3
