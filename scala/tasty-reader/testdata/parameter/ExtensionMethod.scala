package parameter

trait ExtensionMethod {
  extension (i: Int)
    def typeParameter[A]: Unit = ???

  extension (i: Int)
    def typeParameters[A, B]: Unit = ???

  extension (i: Int)
    def valueParameter(x: Int): Unit

  extension (i: Int)
    def valueParameters(x: Int, y: Long): Unit

  extension (i: Int)
    def noClause: Unit

  extension (i: Int)
    def emptyClause(): Unit

  extension (i: Int)
    def multipleClauses(x: Int)(y: Long): Unit

  extension (i: Int)
    def multipleClausesEmpty1()(y: Long): Unit

  extension (i: Int)
    def multipleClausesEmpty2(x: Int)(): Unit

  extension (i: Int)
    def multipleClausesEmpty3()(): Unit

  extension (i: Int)
    def typeAndValueParameters[A](x: Int): Unit
}