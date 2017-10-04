1
object A {
  case class Triple[A,B](a: A, b: B)

  implicit val t = new Triple("", 1d)

  def foo[B](list: List[AnyRef])(implicit t2: Triple[String, B]) : Triple[String, B] = t2

  val res = foo(List())
  /*start*/res.b.isInfinite/*end*/
}
//Boolean