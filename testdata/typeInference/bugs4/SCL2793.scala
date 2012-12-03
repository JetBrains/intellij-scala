trait ObjectGraphMatchers {
  trait A
  trait B
  case class Zoo[-T](x: T)
  def foo[T](x: Zoo[T], y: Zoo[T]): T = x.x

  /*start*/foo(Zoo[A](new A {}), Zoo[B](new B {}))/*end*/
}
/*
Few variants:
ObjectGraphMatchers.this.type#B with ObjectGraphMatchers.this.type#A
ObjectGraphMatchers.this.type#A with ObjectGraphMatchers.this.type#B
 */