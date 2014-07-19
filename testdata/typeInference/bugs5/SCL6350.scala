object Test {
  def apply(block: String => Int) = block("test")

  def apply( block: => Int) = block
}


object Foo {

  val Test2 = Test

  def foo = Test { arg =>   // works fine
    1
  }

  def foo2 = Test2 { arg => // reports errors: "Cannot resolve method Test2.apply" and "Missing parameter type: arg"
    /*start*/arg/*end*/
    1
  }
}
//String