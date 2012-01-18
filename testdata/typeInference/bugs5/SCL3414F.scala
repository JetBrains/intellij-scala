class A {
  def foo = goo
  def goo = /*start*/foo/*end*/
}
//Any