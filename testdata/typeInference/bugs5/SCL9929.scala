val base: Option[IndexedSeq[String]] = ???

val problem = base.getOrElse(???).map(_.size)

val tmp = base.getOrElse(???)
val correct = tmp.map(_.size)

/*start*/problem/*end*/

//IndexedSeq[Int]