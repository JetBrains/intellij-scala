case class A(i: Int)

List.empty[A].foreach {
  case A(i<caret>) => ???
}
//TEXT: i: Int, STRIKEOUT: false