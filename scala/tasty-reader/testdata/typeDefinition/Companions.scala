package typeDefinition

trait Companions {
  class Class {
    def instance: Int = ???
  }

  object Class {
    def static: Int = ???
  }

  class Trait {
    def instance: Int = ???
  }

  object Trait {
    def static: Int = ???
  }

  case class CaseClass(x: Int) {
    def instance: Int = ???
  }

  object CaseClass {
    def static: Int = ???
  }

  enum Enum {
    def instance: Int = ???

    case Case
  }

  object Enum {
    def static: Int = ???
  }
}