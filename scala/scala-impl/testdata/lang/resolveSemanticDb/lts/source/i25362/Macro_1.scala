package example
import scala.deriving.Mirror
import scala.quoted.*

class TC[A] {
  type Out
}

object TC {
  transparent inline def derived[Left](using m: Mirror.ProductOf[Left]): TC[Left] =
    ${ derivedImpl[Left, m.MirroredElemTypes] }

  private def derivedImpl[Left: Type, LeftTypes: Type](using Quotes): Expr[TC[Left]] =
    '{ new TC[Left] { type Out = LeftTypes } }
}
