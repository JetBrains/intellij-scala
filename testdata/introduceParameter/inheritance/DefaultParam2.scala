//default = true
1
class A {
  def foo() = {
  }
}

class B extends A {
  override def foo() = {
    /*start*/1/*end*/
  }
}
/*
//default = true
1
class A {
  def foo(param: Int = 1): Unit = {
  }
}

class B extends A {
  override def foo(param: Int): Unit = {
    /*start*/param/*end*/
  }
}
*/