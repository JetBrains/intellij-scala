//default = true
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
//default = true
1
class A {
  def foo(param: Int = 1) {
    /*start*/param/*end*/
  }
}

class B extends A {
  override def foo(param: Int) {

  }
}
*/