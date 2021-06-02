package typeDefinition

trait Parents {
  trait NoParameters

  trait TypeParameter[A]

  trait TypeParameters[A, B]

  trait ValueParameter(x: Int)

  trait ValueParameters(x: Int, y: Int)

  trait MultipleClauses(x: Int)(y: Int)

  trait TypeAndValueParameter[A](x: Int)

  class Class1 extends NoParameters

  class Class2 extends TypeParameter[Int]

  class Class3 extends TypeParameters[Int, Long]

  class Class4 extends NoParameters with TypeParameter[Int]

  class Class5 extends NoParameters with TypeParameter[Int] with TypeParameters[Int, Long]

  class Class6 extends ValueParameter/**/(1)/**/

  class Class7 extends ValueParameters/**/(1, 2)/**/

  class Class8 extends MultipleClauses/**/(1)(2)/**/

  class Class9 extends TypeAndValueParameter[Int]/**/(1)/**/

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

  object Object7 extends ValueParameters/**/(1, 2)/**/

  object Object8 extends MultipleClauses/**/(1)(2)/**/

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

  enum Enum4 extends NoParameters with TypeParameter[Int] {
    case Case
  }

  enum Enum5 extends NoParameters with TypeParameter[Int] with TypeParameters[Int, Long] {
    case Case
  }

  enum Enum6 extends ValueParameter/**/(1)/**/ {
    case Case
  }

  enum Enum7 extends ValueParameters/**/(1, 2)/**/ {
    case Case
  }

  enum Enum8 extends MultipleClauses/**/(1)(2)/**/ {
    case Case
  }

  enum Enum9 extends TypeAndValueParameter[Int]/**/(1)/**/ {
    case Case
  }

  enum EnumCaseObject1 {
    case CaseObject extends EnumCaseObject1 with NoParameters
  }

  enum EnumCaseObject2 {
    case CaseObject extends EnumCaseObject2 with TypeParameter[Int]
  }

  enum EnumCaseObject3 {
    case CaseObject extends EnumCaseObject3 with TypeParameters[Int, Long]
  }

  enum EnumCaseObject4 {
    case CaseObject extends EnumCaseObject4 with NoParameters with TypeParameter[Int]
  }

  enum EnumCaseObject5 {
    case CaseObject extends EnumCaseObject5 with NoParameters with TypeParameter[Int] with TypeParameters[Int, Long]
  }

  enum EnumCaseObject6 {
    case CaseObject extends EnumCaseObject6 with ValueParameter/**/(1)/**/
  }

  enum EnumCaseObject7 {
    case CaseObject extends EnumCaseObject7 with ValueParameters/**/(1, 2)/**/
  }

  enum EnumCaseObject8 {
    case CaseObject extends EnumCaseObject8 with MultipleClauses/**/(1)(2)/**/
  }

  enum EnumCaseObject9 {
    case CaseObject extends EnumCaseObject9 with TypeAndValueParameter[Int]/**/(1)/**/
  }

  enum EnumCaseClass1 {
    case CaseClass() extends EnumCaseClass1 with NoParameters
  }

  enum EnumCaseClass2 {
    case CaseClass() extends EnumCaseClass2 with TypeParameter[Int]
  }

  enum EnumCaseClass3 {
    case CaseClass() extends EnumCaseClass3 with TypeParameters[Int, Long]
  }

  enum EnumCaseClass4 {
    case CaseClass() extends EnumCaseClass4 with NoParameters with TypeParameter[Int]
  }

  enum EnumCaseClass5 {
    case CaseClass() extends EnumCaseClass5 with NoParameters with TypeParameter[Int] with TypeParameters[Int, Long]
  }

  enum EnumCaseClass6 {
    case CaseClass() extends EnumCaseClass6 with ValueParameter/**/(1)/**/
  }

  enum EnumCaseClass7 {
    case CaseClass() extends EnumCaseClass7 with ValueParameters/**/(1, 2)/**/
  }

  enum EnumCaseClass8 {
    case CaseClass() extends EnumCaseClass8 with MultipleClauses/**/(1)(2)/**/
  }

  enum EnumCaseClass9 {
    case CaseClass() extends EnumCaseClass9 with TypeAndValueParameter[Int]/**/(1)/**/
  }
}