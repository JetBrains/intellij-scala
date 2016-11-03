class TestCats {
  import cats.syntax.all._
  import cats.std.all._
  import cats.data.Validated

  def valida(a: Int): Validated[List[String], Int] = {
    if (a > 0) Validated.valid(a)
    else Validated.invalid(List("That number sucks"))
  }

  val q = valida(4)
  val w = valida(-2)

  /*start*/q |@| w/*end*/
}
//CartesianOps[T, Int]