class A[T](x: T) {
  def foo[T](s: T = x): T = s
}
val a = new A("")
/*start*/a.foo()/*end*/
//String