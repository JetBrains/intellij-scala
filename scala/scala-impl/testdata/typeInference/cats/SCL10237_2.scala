class TestCats {
  import cats.syntax.all._
  import cats.std.all._

  val q = List(1)
  val w = List(2)
  /*start*/q |@| w/*end*/
}
//CartesianBuilder[List]#CartesianBuilder2[Int, Int]