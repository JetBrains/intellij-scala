trait Nesting {
  class Level1 {
    class Level2 {
      class Level3
    }
  }

  class Class {
    trait Trait

    object Object

    def method: Int = ???
  }

  trait Trait {
    class Class

    object Object

    def method: Int = ???
  }

  object Object {
    class Class

    trait Trait

    def method: Int = ???
  }

  def method: Unit = /**/{
    class LocalClass
    trait LocalTrait
    object LocalObject
    def localMethod: Int = ???
  }/*???*/
}