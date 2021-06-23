package parameter

trait Repeated {
  def method(xs: Int*): Unit

  class Class(xs: Int*)

  trait Trait(xs: Int*)

  case class CaseClass(xs: Int*)

  enum Enum(xs: Int*) {
    case Case/**/ extends Enum(1)/**/
  }

  enum EnumCaseClass {
    case Class(xs: Int*)
  }

  extension (i: Int)
    def extensionMethod(x: Int*): Unit = ???

  trait T

  given givenAlias(using i: Int*): T = ???

  given givenInstance(using i: Int*): T with {}
}