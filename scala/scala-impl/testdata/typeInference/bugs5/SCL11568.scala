import java.util.function.BinaryOperator

object Test {
  def combiner1: BinaryOperator[Int] = /*start*/(one: Int, other: Int) => one + other/*end*/
}

// BinaryOperator[Int]