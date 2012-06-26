object tedfdst {
  def apply[A](a: A): Option[A] = null

  implicit def s2i(s: String): Int = 0

  val thiss = this
  val y: Option[Int] = thiss(/*start*/""/*end*/)
}
/*
Seq(any2ArrowAssoc,
    any2Ensuring,
    any2stringadd,
    any2stringfmt,
    augmentString,
    s2i,
    wrapString),
Some(s2i)
*/