def producePairs[T <% Traversable[String]](words: T): Map[(String, String), Double] = {
  val counts = words.groupBy(identity).map {
    case (w, ws) => (w -> ws.size)
  }
  val size = (counts.size * counts.size).toDouble
  for (w1 <- counts; w2 <- counts) yield {
    /*start*/((w1._1, w2._1) -> ((w1._2 * w2._2) / size))/*end*/
  }
}
//((String, String), Double)