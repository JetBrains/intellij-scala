case class CaseClass(x: Float)

22f match {
  case CaseClass(<caret>) =>
}
//TEXT: x: Float, STRIKEOUT: false