trait SCL7413 {
  implicit val l: List[Int]

  def foo[T](implicit l: List[T]): Option[T] = l.headOption

  /*start*/foo/*end*/
}
//Option[Int]