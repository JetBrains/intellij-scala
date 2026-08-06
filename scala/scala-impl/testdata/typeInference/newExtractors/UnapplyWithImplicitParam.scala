object UnapplyWithImplicitParam {
  object A {
    def unapply(i: Int)(implicit b: Boolean): Option[Int] = if (b) Some(i) else None
  }
  implicit val b: Boolean = true

  val A(i) = 42
  /*start*/i/*end*/
}
//Int