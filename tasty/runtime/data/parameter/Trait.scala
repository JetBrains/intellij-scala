package parameter

trait Trait {
  trait TypeParameter[A]

  trait TypeParameters[A, B]

  trait ValueParameter(x: Int)

  trait ValueParameters(x: Int, y: Int)

  trait EmptyClause/**/()/**/

  trait MultipleClauses(x: Int)(y: Int)

  trait TypeAndValueParameters[A](x: Int)
}