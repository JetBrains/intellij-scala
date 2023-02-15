package annotation

trait Parameters {
  def methodType[@inline A]: Unit

  def methodValue(@inline x: Int): Unit

  def methodRepeated(@inline x: Int*): Unit

  def methodByName(@inline x: => Int): Unit

  def methodDefaultArgument(@inline x: Int = ???): Unit

  extension [@inline A](i: Int)
    def extensionType: Unit = ???

  extension (@inline i: Int)
    def extensionValue: Unit = ???

  extension (i: Int)(using @inline x: Int)
    def extensionUsing: Unit = ???

  extension (i: Int)
    def extensionMethodType[@inline A]: Unit = ???

  extension (i: Int)
    def extensionMethodValue(@inline x: Int): Unit

  class ClassType[@inline A]

  class ClassTypeBound[@inline A <: Int]

  class ClassTypeVariance[@inline +A]

  class ClassHKTVariance[A[@inline X]]

  class ClassValue(@inline x: Int)

  class ClassVal(@inline val x: Int)

  class ClassVar(@inline var x: Int)

  class ClassPrivateVal(@inline /**/private val /**/x: Int)

  class CaseClass(@inline x: Int)

  class TraitType[@inline A]

  class TraitValue(@inline x: Int)

  enum EnumType[@inline A] {
    case Case extends EnumType[Int]
  }

  enum EnumValue(@inline x: Int) {
    case Case/**/ extends EnumValue(1)/**/
  }

  enum EnumCaseClassType {
    case Class[@inline A]()
  }

  enum EnumCaseClassValue {
    case Class(@inline x: Int)
  }

  trait T1

  trait T2

  trait T3

  trait T4

  given aliasType[@inline A]: Int = ???

  given aliasValue(using @inline x: Int): Int = ???

  given [@inline A]: T1 = ???

  given (using @inline x: Int): T2 = ???

  given instanceType[@inline A]: T1 with {}

  given instanceValue(using x: Int): T2 with {}

  given [@inline A](using x: Int): T3 with {}

  given (using @inline x: Int): T4 with {}
}