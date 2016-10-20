class TestCats {
  import cats.syntax.all._
  import cats.std.all._

  val q = Map(1 -> "foo")
  val w = Map(2 -> "bar")
  /*start*/q |@| w/*end*/
}
//CartesianBuilder[List]#CartesianBuilder2[Int, Int]