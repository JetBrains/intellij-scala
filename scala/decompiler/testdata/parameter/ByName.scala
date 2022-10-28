package parameter

trait ByName {
  def method(x: => Int): Unit

  class Class(x: => Int)
}