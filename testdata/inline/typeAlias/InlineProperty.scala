object MatchTest2 extends App {
  type /*caret*/My = List[Int]
  val a = MatchTest2
  val my: a.My = List(1)
}
/*
object MatchTest2 extends App {
  val a = MatchTest2
  val my: List[Int] = List(1)
}
*/