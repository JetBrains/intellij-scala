package typeDefinition

trait Parents {
  trait NoParameters

  trait TypeParameter[A]

  trait TypeParameters[A, B]

  trait ValueParameter(x: Int)

  trait ValueParameters(x: Int, y: Long)

  trait MultipleClauses(x: Int)(y: Long)

  trait TypeAndValueParameter[A](x: Int)

  trait InferredTypeParameters[A, B](x: A, y: B)

  class Class1 extends NoParameters

  class Class2 extends TypeParameter[Int]

  class Class3 extends TypeParameters[Int, Long]

  class Class4 extends NoParameters, TypeParameter[Int]

  class Class5 extends NoParameters, TypeParameter[Int], TypeParameters[Int, Long]

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

  trait Trait4 extends NoParameters, TypeParameter[Int]

  trait Trait5 extends NoParameters, TypeParameter[Int], TypeParameters[Int, Long]

  trait Trait6 extends ValueParameter

  trait Trait7 extends ValueParameters

  trait Trait8 extends MultipleClauses

  trait Trait9 extends TypeAndValueParameter[Int]

  object Object1 extends NoParameters

  object Object2 extends TypeParameter[Int]

  object Object3 extends TypeParameters[Int, Long]

  object Object4 extends NoParameters, TypeParameter[Int]

  object Object5 extends NoParameters, TypeParameter[Int], TypeParameters[Int, Long]

  object Object6 extends ValueParameter/**/(1)/**/

  object Object7 extends ValueParameters/**/(1, 2L)/**/

  object Object8 extends MultipleClauses/**/(1)(2L)/**/

  object Object9 extends TypeAndValueParameter[Int]/**/(1)/**/

  enum Enum1 extends NoParameters {
    case Case
  }

  enum Enum2 extends TypeParameter[Int] {
    case Case
  }

  enum Enum3 extends TypeParameters[Int, Long] {
    case Case
  }

  enum Enum4 extends NoParameters, TypeParameter[Int] {
    case Case
  }

  enum Enum5 extends NoParameters, TypeParameter[Int], TypeParameters[Int, Long] {
    case Case
  }

  enum Enum6 extends ValueParameter/**/(1)/**/ {
    case Case
  }

  enum Enum7 extends ValueParameters/**/(1, 2L)/**/ {
    case Case
  }

  enum Enum8 extends MultipleClauses/**/(1)(2L)/**/ {
    case Case
  }

  enum Enum9 extends TypeAndValueParameter[Int]/**/(1)/**/ {
    case Case
  }

  enum EnumCaseObject1 {
    case CaseObject extends EnumCaseObject1, NoParameters
  }

  enum EnumCaseObject2 {
    case CaseObject extends EnumCaseObject2, TypeParameter[Int]
  }

  enum EnumCaseObject3 {
    case CaseObject extends EnumCaseObject3, TypeParameters[Int, Long]
  }

  enum EnumCaseObject4 {
    case CaseObject extends EnumCaseObject4, NoParameters, TypeParameter[Int]
  }

  enum EnumCaseObject5 {
    case CaseObject extends EnumCaseObject5, NoParameters, TypeParameter[Int], TypeParameters[Int, Long]
  }

  enum EnumCaseObject6 {
    case CaseObject extends EnumCaseObject6, ValueParameter/**/(1)/**/
  }

  enum EnumCaseObject7 {
    case CaseObject extends EnumCaseObject7, ValueParameters/**/(1, 2L)/**/
  }

  enum EnumCaseObject8 {
    case CaseObject extends EnumCaseObject8, MultipleClauses/**/(1)(2L)/**/
  }

  enum EnumCaseObject9 {
    case CaseObject extends EnumCaseObject9, TypeAndValueParameter[Int]/**/(1)/**/
  }

  enum EnumCaseClass1 {
    case CaseClass() extends EnumCaseClass1, NoParameters
  }

  enum EnumCaseClass2 {
    case CaseClass() extends EnumCaseClass2, TypeParameter[Int]
  }

  enum EnumCaseClass3 {
    case CaseClass() extends EnumCaseClass3, TypeParameters[Int, Long]
  }

  enum EnumCaseClass4 {
    case CaseClass() extends EnumCaseClass4, NoParameters, TypeParameter[Int]
  }

  enum EnumCaseClass5 {
    case CaseClass() extends EnumCaseClass5, NoParameters, TypeParameter[Int], TypeParameters[Int, Long]
  }

  enum EnumCaseClass6 {
    case CaseClass() extends EnumCaseClass6, ValueParameter/**/(1)/**/
  }

  enum EnumCaseClass7 {
    case CaseClass() extends EnumCaseClass7, ValueParameters/**/(1, 2L)/**/
  }

  enum EnumCaseClass8 {
    case CaseClass() extends EnumCaseClass8, MultipleClauses/**/(1)(2L)/**/
  }

  enum EnumCaseClass9 {
    case CaseClass() extends EnumCaseClass9, TypeAndValueParameter[Int]/**/(1)/**/
  }

  given givenInstance1: NoParameters with {}

  given givenInstance2: TypeParameter[Int] with {}

  given givenInstance3: TypeParameters[Int, Long] with {}

  given givenInstance4: NoParameters with TypeParameter[Int] with {}

  given givenInstance5: NoParameters with TypeParameter[Int] with TypeParameters[Int, Long] with {}

  given givenInstance6: ValueParameter/**/(1)/**/ with {}

  given givenInstance7: ValueParameters/**/(1, 2L)/**/ with {}

  given givenInstance8: MultipleClauses/**/(1)(2L)/**/ with {}

  given givenInstance9: TypeAndValueParameter[Int]/**/(1)/**/ with {}

  given NoParameters with {}

  given TypeParameter[Int] with {}

  given TypeParameters[Int, Long] with {}

  given NoParameters with TypeParameter[Int] with {}

  given NoParameters with TypeParameter[Int] with TypeParameters[Int, Long] with {}

  given ValueParameter/**/(1)/**/ with {}

  given ValueParameters/**/(1, 2L)/**/ with {}

  given MultipleClauses/**/(1)(2L)/**/ with {}

  given TypeAndValueParameter[Int]/**/(1)/**/ with {}
}