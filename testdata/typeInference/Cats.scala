class Cats {
  import cats._, cats.syntax.traverse._, cats.std.all._
  val xs: List[Either[String, Int]] = List(Right(1), Right(2))
  val r = /*start*/xs.sequenceU/*end*/
}
//simplified: Either[String, List[Int]]