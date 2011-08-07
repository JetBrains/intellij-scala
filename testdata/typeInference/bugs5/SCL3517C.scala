def foo[A, B] {
  val a = null.asInstanceOf[A]
  implicit def BToString = (I: B) => ""
  /*start*/a/*end*/: String // bad code green
}
//A