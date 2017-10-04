class Expr[T] {
  def * (other: Expr[T]): Expr[T] = null
}

case class Const[T](value: T) extends Expr[T]
case class ExtDouble(value: Double)

object Test extends App {
  implicit def double2Const[T](value: Double): Const[T] = Const(value.asInstanceOf[T])

  /*start*/2.0 * Const[ExtDouble](ExtDouble(2.0))/*end*/
}
//Expr[ExtDouble]