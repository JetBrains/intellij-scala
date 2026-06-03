1
class A {
  def foo(x: Int*) {
    /*start*/1/*end*/
  }
}

class B extends A {
  override def foo(x: Int*) {

  }
}
/*
1
class A {
  def foo(param: Int, x: Int*) {
    /*start*/param/*end*/
  }
}

class B extends A {
  override def foo(param: Int, x: Int*) {

  }
}
*/