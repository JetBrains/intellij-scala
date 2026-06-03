trait Request[T] { def f: T }
case class StringRequest(s: List[Int]) extends Request[List[Int]] { def f = ??? }

object Test {
  def f[T](r: Request[T]): T = r match {
    case StringRequest(s) => /*start*/List.empty/*end*/
    case _ => r.f
  }
}

//List[Nothing]