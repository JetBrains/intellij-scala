val a: Option[Tuple2[String, Int]] = None
a match {
  case Some((x, _)) => /*start*/x/*end*/
}
//String