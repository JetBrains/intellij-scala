class A[T](x: T) {
  def foo[S](s: S = x): S = s
}
val a = new A("")
/*start*/a.foo()/*end*/
//String