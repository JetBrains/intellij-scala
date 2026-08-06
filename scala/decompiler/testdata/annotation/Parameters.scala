package annotation

trait Parameters {
  def methodType[@inline A]: Unit

  def methodValue(@inline x: Int): Unit

  def methodRepeated(@inline x: Int*): Unit

  def methodByName(@inline x: => Int): Unit

  def methodDefaultArgument(@inline x: Int = ???): Unit

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
}