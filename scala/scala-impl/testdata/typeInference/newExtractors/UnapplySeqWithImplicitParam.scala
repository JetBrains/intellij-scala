object UnapplySeqWithImplicitParam {
  object A {
    def unapplySeq(i: Int)(implicit b: Boolean): Option[Seq[Int]] = Some(if (b) Seq(i, i) else Nil)
  }
  implicit val b: Boolean = true

  val A(i1, i2) = 42
  /*start*/i2/*end*/
}
//Int