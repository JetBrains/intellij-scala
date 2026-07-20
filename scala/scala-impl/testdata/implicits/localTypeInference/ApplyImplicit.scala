object tedfdst {
  def apply[A](a: A): Option[A] = null

  implicit def s2i(s: String): Int = 0

  val thiss = this
  val y: Option[Int] = thiss(/*start*/""/*end*/)
}
/*
Seq(ArrowAssoc,
    Ensuring,
    StringFormat,
    augmentString,
    s2i,
    wrapString,
    any2stringadd),
Some(s2i)
*/