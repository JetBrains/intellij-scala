trait Unapply {
  def unapply(x: Int): Option[Int] = Some(x)
}

object X {
  val A: Any with Unapply = null
}
22 match {
  case X.A(<caret>) =>
}
//Int