class A {
  class B
  val value: B = new B
}

trait T { this: A =>
  def getList(): B = {
    /*start*/value/*end*/
  }
}
//T.this.B