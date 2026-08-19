object Example {
 val z: Option[Int] = Some(1)
  z match {
    case Some(<caret>) =>
  }
}
//TEXT: value: Int, STRIKEOUT: false