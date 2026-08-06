package parameter

trait Class {
  class TypeParameter[A]

  class TypeParameters[A, B]

  class ValueParameter(x: Int)

  class ValueParameters(x: Int, y: Long)

  class EmptyClause/**/()/**/

  class MultipleClauses(x: Int)(y: Long)

  class MultipleClausesEmpty1()(y: Long)

  class MultipleClausesEmpty2(x: Int)()

  class MultipleClausesEmpty3()()

  class TypeAndValueParameters[A](x: Int)
}