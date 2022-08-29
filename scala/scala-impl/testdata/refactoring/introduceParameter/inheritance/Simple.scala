1
class A {
  def foo() {
    /*start*/1/*end*/
  }
}

class B extends A {
  override def foo() {

  }
}
/*
1
class A {
  def foo(param: Int) {
    /*start*/param/*end*/
  }
}

class B extends A {
  override def foo(param: Int) {

  }
}
*/