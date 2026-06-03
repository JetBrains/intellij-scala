object SCL5356 {
  type Elem = Either[Int, Float]

  def elemToDouble(e: Elem): Double = e.fold(_.toDouble, _.toDouble) // ok

  def elemToString(e: Elem): String = e.fold(_.toString, /*start*/_.toString/*end*/) // second toString not resolved
}
//Float => String