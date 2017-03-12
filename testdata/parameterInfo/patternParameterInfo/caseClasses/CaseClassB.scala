case class CaseClassB(x: Float, y: Int)

22f match {
  case CaseClassB(_, <caret>) =>
}
//x: Float, y: Int