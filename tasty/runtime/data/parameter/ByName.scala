package parameter

trait ByName {
  def method(x: => Int): Unit

  class Class(x: => Int)

  enum Enum(x: => Int) {
    case Case extends Enum/**/(1)/**/
  }

  extension (i: => Int)
    def extension: Unit = ???

  extension (i: Int)
    def extensionMethod(x: => Int): Unit = ???
}