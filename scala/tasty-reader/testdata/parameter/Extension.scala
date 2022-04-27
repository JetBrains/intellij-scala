package parameter

trait Extension {
  extension [A](i: Int)
    def typeParameter: Unit = ???

  extension [A, B](i: Int)
    def typeParameters: Unit = ???

  extension (i: Int)(using x: Int)
    def usingParameter: Unit = ???

  extension (i: Int)(using x: Int, y: Int)
    def usingParameters: Unit = ???

  extension (i: Int)(using x: Int)(using y: Int)
    def multipleUsingClauses: Unit = ???

  extension [A](i: Int)
    def combinedTypeParameters[B]: Unit = ???

  extension (i: Int)(using x: Int)/**//*(using y: Int)*/
    def combinedUsingClauses/**/(using y: Int)/**/: Unit = ???

  extension [A](i: Int)(using x: Int)
    def combinedTypeParametersAndUsingClauses[B](using y: Int): Unit = ???

  extension [A](i: Int)(using x: Int)
    def combinedValueParametersTypeParametersAndUsingClauses[B](y: Int)(using z: Int): Unit = ???
}