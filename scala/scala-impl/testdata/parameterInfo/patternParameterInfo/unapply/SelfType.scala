trait Unapply {
  def unapply(x: Int): Option[Int] = Some(x)
}

object X {
  Self: Unapply =>
  22 match {
    case Self(<caret>) =>
  }
}
//Int