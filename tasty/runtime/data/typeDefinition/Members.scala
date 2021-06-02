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
}