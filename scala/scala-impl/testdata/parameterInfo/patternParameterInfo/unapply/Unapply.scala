object Unapply {
  def unapply(x: Int): Option[Int] = Some(x)
}

22 match {
  case Unapply(<caret>) =>
}
//Int