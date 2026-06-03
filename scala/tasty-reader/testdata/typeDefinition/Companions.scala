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

  class SerializableClass extends Serializable

  object SerializableClass

  enum Enum1 {
    def instance: Int = ???

    case Case
  }

  object Enum1 {
    def static: Int = ???
  }

  object Enum2 {
    def static: Int = ???
  }

  enum Enum2 {
    def instance: Int = ???

    case Case
  }
}