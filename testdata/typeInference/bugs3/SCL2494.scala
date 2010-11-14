object ExtractorTupleNoSugar {
  def unapply(xs: Any): Option[Tuple2[Int, Int]] = error("")
}

val (ExtractorTupleNoSugar(h0, t0)) = Stream(1, 1)
/*start*/h0/*end*/
//Int