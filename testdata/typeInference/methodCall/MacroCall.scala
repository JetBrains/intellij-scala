object A {
  def foo(x: Int): Int = macro X.x
  /*start*/foo(0)/*end*/
}
//Int