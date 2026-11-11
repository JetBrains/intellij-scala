package parameter

trait Trait {
  trait TypeParameter[A]

  trait TypeParameters[A, B]

  trait ValueParameter(x: Int)

  trait ValueParameters(x: Int, y: Long)

  trait EmptyClause/**/()/**/

  trait MultipleClauses(x: Int)(y: Long)

  trait MultipleClausesEmpty1()(y: Long)

  trait MultipleClausesEmpty2(x: Int)()

  trait MultipleClausesEmpty3()()

  trait TypeAndValueParameters[A](x: Int)
}