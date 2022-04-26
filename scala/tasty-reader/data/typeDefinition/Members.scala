package typeDefinition

trait Members {
  case class CaseClass(x: Int) {
    def member: Int = ???
  }

  class Class {
    def member: Int = ???
  }

  implicit class ImplicitClass(val x: Int) {
    def member: Int = ???
  }

  class Object {
    def member: Int = ???
  }

  class Trait {
    def member: Int = ???
  }

  enum Enum {
    def member: Int = ???

    case Case
  }

  trait T

  given givenInstance: T with {
    def member: Int = ???
  }

  given T with {
    def member: Int = ???
  }

  given givenInstanceUsing(using x: Int): T with {
    def member: Int = ???
  }
}