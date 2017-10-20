def main(args: Array[String]) {
  val list = List("a", "b", "c")
  /*start*/for (x <- 1 to 10) yield (x, list.headOption)/*end*/
}
//IndexedSeq[(Int, Option[String])]