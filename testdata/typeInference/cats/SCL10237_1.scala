class TestCats {
  import cats.syntax.all._
  import cats.std.all._

  val q = Map(1 -> "foo")
  val w = Map(2 -> "bar")
  /*start*/q |@| w/*end*/
}
//CartesianBuilder[Unapply.Aux2Right[Cartesian, Map[Int, String], Map, Int, String]#M]#CartesianBuilder2[Unapply.Aux2Right[Cartesian, Map[Int, String], Map, Int, String]#A, String]