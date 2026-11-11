package parameter

trait Given {
  trait T1

  trait T2

  trait T3

  trait T4

  trait T5

  trait T6

  trait T7

  trait T8

  trait T9

  trait T10

  trait T11

  trait T12

  given aliasTypeParameter[A]: Int = ???

  given aliasTypeParameters[A, B]: Int = ???

  given aliasValueParameter(using x: Int): Int = ???

  given aliasValueParameters(using x: Int, y: Int): Int = ???

  given aliasMultipleClauses(using x: Int)(using y: Int): Int = ???

  given aliasTypeAndValueParameter[A](using x: Int): Int = ???

  given [A]: T1 = ???

  given [A, B]: T2 = ???

  given (using x: Int): T3 = ???

  given (using x: Int, y: Int): T4 = ???

  given (using x: Int)(using y: Int): T5 = ???

  given [A](using x: Int): T6 = ???

  given instanceTypeParameter[A]: T1 with {}

  given instanceTypeParameters[A, B]: T2 with {}

  given instanceValueParameter(using x: Int): T3 with {}

  given instanceValueParameters(using x: Int, y: Int): T4 with {}

  given instanceMultipleClauses(using x: Int)(using y: Int): T5 with {}

  given instanceTypeAndValueParameter[A](using x: Int): T6 with {}

  given [A]: T7 with {}

  given [A, B]: T8 with {}

  given (using x: Int): T9 with {}

  given (using x: Int, y: Int): T10 with {}

  given (using x: Int)(using y: Int): T11 with {}

  given [A](using x: Int): T12 with {}
}