package typeDefinition

trait Parents {
  trait NoParameters

  trait TypeParameter[A]

  trait TypeParameters[A, B]

  class ValueParameter(x: Int)

  class ValueParameters(x: Int, y: Long)

  class MultipleClauses(x: Int)(y: Long)

  class TypeAndValueParameter[A](x: Int)

  class InferredTypeParameters[A, B](x: A, y: B)

  class Class1 extends NoParameters

  class Class2 extends TypeParameter[Int]

  class Class3 extends TypeParameters[Int, Long]

  class Class4 extends NoParameters with TypeParameter[Int]

  class Class5 extends NoParameters with TypeParameter[Int] with TypeParameters[Int, Long]

  class Class6 extends ValueParameter/**/(1)/**/

  class Class7 extends ValueParameters/**/(1, 2L)/**/

  class Class8 extends MultipleClauses/**/(1)(2L)/**/

  class Class9 extends TypeAndValueParameter[Int]/**/(1)/**/

  class Class10 extends InferredTypeParameters/**/(1, 2L)/*[Int, Long]*/

  case class CaseClass1() extends NoParameters

  abstract class NonCaseClass1 extends Product

  abstract class NonCaseClass2 extends Serializable

  trait Trait1 extends NoParameters

  trait Trait2 extends TypeParameter[Int]

  trait Trait3 extends TypeParameters[Int, Long]

  trait Trait4 extends NoParameters with TypeParameter[Int]

  trait Trait5 extends NoParameters with TypeParameter[Int] with TypeParameters[Int, Long]

  trait Trait6 extends ValueParameter

  trait Trait7 extends ValueParameters

  trait Trait8 extends MultipleClauses

  trait Trait9 extends TypeAndValueParameter[Int]

  object Object1 extends NoParameters

  object Object2 extends TypeParameter[Int]

  object Object3 extends TypeParameters[Int, Long]

  object Object4 extends NoParameters with TypeParameter[Int]

  object Object5 extends NoParameters with TypeParameter[Int] with TypeParameters[Int, Long]

  object Object6 extends ValueParameter/**/(1)/**/

  object Object7 extends ValueParameters/**/(1, 2L)/**/

  object Object8 extends MultipleClauses/**/(1)(2L)/**/

  object Object9 extends TypeAndValueParameter[Int]/**/(1)/**/
}