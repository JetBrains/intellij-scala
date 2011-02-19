class A[X](x: X) {
  def a: this.type
}

val a: A[String] = new A("")
/*start*/a.a/*end*/
//a.type