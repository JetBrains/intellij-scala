trait Expr[T] {
  def + (other : Expr[T]): Expr[T] = Add(this, other)
}

object Expr {
  implicit def doubleToConst(d: Double) = Const(ExtDouble(d))
  implicit def extDoubleToConst(d: ExtDouble) = Const(d)
}

case class Const[T](value: T) extends Expr[T]
case class Add[T](first: Expr[T], second: Expr[T]) extends Expr[T]

case class ExtDouble(value : Double) {
  def + (other: ExtDouble) = ExtDouble(value + other.value)
}

object ExtDouble {
  implicit def doubleToExtDouble(d: Double) = ExtDouble(d)
}

object HighlightingTest {
  import Expr._              // when you import these implicits
  /*start*/1.0 + ExtDouble(1.0)/*end*/   // Expr[ExtDouble] doesn't conform to ExtDouble.
}
//ExtDouble