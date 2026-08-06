object test1 {
  trait SeqFactory[CC[X]] {
    def unapplySeq[A](x : CC[A]) : scala.Some[CC[A]] = null
  }
  object SeqExtractor extends SeqFactory[Seq]
  val ss: Seq[String] = null
  ss match {
    case SeqExtractor(a) => /*start*/a/*end*/
  }
}
//String