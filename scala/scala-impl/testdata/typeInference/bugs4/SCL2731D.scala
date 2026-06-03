object test4 {
  abstract class SeqFactory[CC[X]] {
    def unapplySeq[A](x : Seq[A]) : scala.Some[Seq[A]] = null
  }
  object SeqExtractor extends SeqFactory[Seq]
  val ss: Seq[String] = null
  ss match {
    case SeqExtractor(a) => /*start*/a/*end*/
  }
}
//String