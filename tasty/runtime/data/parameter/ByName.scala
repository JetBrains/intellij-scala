package parameter

trait ByName {
  def method(x: => Int): Unit

  class Class(x: => Int)

  enum Enum(x: => Int) {
    case Case extends Enum/**/(1)/**/
  }
}