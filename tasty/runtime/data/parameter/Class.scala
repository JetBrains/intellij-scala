package parameter

trait Class {
  class TypeParameter[A]

  class TypeParameters[A, B]

  class ValueParameter(x: Int)

  class ValueParameters(x: Int, y: Long)

  class EmptyClause/**/()/**/

  class MultipleClauses(x: Int)(y: Long)

  class TypeAndValueParameters[A](x: Int)
}