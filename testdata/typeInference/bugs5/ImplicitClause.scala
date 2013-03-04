object ImplicitClause {
  class A
  class B extends A
  class Z[A](a: A)(implicit i: Int)
  implicit val s: Int = 123
  val z: Z[A] = /*start*/new Z(new B)/*end*/
}
//ImplicitClause.Z[ImplicitClause.A]