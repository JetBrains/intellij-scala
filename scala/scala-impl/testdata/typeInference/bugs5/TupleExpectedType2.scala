object TupleExpectedType2 {
  implicit def str2boolean(s: String): Boolean = s.nonEmpty
  val x: String = "text"
  val z: Option[(Int, Boolean)] = Option(1, /*start*/x/*end*/)
}
//Boolean