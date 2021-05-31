package typeDefinition

trait Parents {
  trait NoParameters

  trait TypeParameter[A]

  trait TypeParameters[A, B]

  trait ValueParameter(x: Int)

  trait TypeAndValueParameter[A](x: Int)

  class Class1 extends NoParameters

  class Class2 extends TypeParameter[Int]

  class Class3 extends TypeParameters[Int, Long]

  class Class4 extends NoParameters with TypeParameter[Int]

  class Class5 extends NoParameters with TypeParameter[Int] with TypeParameters[Int, Long]

  class Class6 extends ValueParameter(???)

  class Class7 extends TypeAndValueParameter[Int](???)

  trait Trait1 extends NoParameters

  trait Trait2 extends TypeParameter[Int]

  trait Trait3 extends TypeParameters[Int, Long]

  trait Trait4 extends NoParameters with TypeParameter[Int]

  trait Trait5 extends NoParameters with TypeParameter[Int] with TypeParameters[Int, Long]

  trait Trait6 extends ValueParameter

  trait Trait7 extends TypeAndValueParameter[Int]

  object Object1 extends NoParameters

  object Object2 extends TypeParameter[Int]

  object Object3 extends TypeParameters[Int, Long]

  object Object4 extends NoParameters with TypeParameter[Int]

  object Object5 extends NoParameters with TypeParameter[Int] with TypeParameters[Int, Long]

  object Object6 extends ValueParameter(???)

  object Object7 extends TypeAndValueParameter[Int](???)
}