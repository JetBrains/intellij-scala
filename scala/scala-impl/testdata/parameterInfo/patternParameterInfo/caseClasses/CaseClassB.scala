case class CaseClassB(x: Float, y: Int)

22f match {
  case CaseClassB(_, <caret>) =>
}
//TEXT: x: Float, y: Int, STRIKEOUT: false