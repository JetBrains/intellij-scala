trait Parser[+T]

sealed trait Result[+T]

object Result{

  case class Success[+T](value: T, index: Int) extends Result[T]

  case class Failure(input: String,
                     index: Int,
                     lastParser: Parser[_],
                     traceData: (Int, Parser[_])) extends Result[Nothing]

  object Failure {
    def unapply[T](x: Result[T]): Option[(Parser[_], Int)] = ???
  }
}