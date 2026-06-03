case class ForGenerator(iParam: Int) {
  val iSeq = Seq(1, 23)
  for {
    ir <- iSeq

    if i<caret>
  } yield ir
}

