object Test {
  class A

  implicit class Z(a: A) {
    val ex = this
    def unapply(x: A): Option[Int] = Some(1)
  }

  val a = new A

  a match {
    case a.ex(x) => /*start*/x/*end*/
  }
}
//Int