package parameter

trait Identifiers {
  class ClassTypeParameter[`class`]

  class ClassValueParameter(`class`: Int)

  def methodTypeParameter[`def`]: Unit

  def methodValueParameter(`def`: Int): Unit

  type TypeParameter[`type`]

  extension (`extension`: Int)
    def method: Unit = ???
}