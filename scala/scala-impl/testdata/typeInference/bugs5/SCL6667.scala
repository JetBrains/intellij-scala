object SCL6667 {

  trait Expression {
    type Repr <: Expression

    def foo = 123
  }

  object Expression {
    trait Aux[T <: Expression.Aux[T]] extends Expression {
      self: T =>
      type Repr = T
    }
  }


  case class SubtractNumeric(expression1: Expression, expression2: Expression) extends Expression.Aux[SubtractNumeric]

  case class DivideNumeric(expression1: Expression, expression2: Expression) extends Expression.Aux[DivideNumeric]

  case class AddNumeric(expressions: List[Expression]) extends Expression.Aux[AddNumeric]

  val x =
    if (true) SubtractNumeric(null, null)
    else if (true) DivideNumeric(null, null)
    else AddNumeric(null)

  /*start*/x.foo/*end*/
}
//Int