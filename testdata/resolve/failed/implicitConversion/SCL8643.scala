trait Prop {
  def ==>(p: => Prop): Prop = ???
}

object Prop {

  class ExtendedBoolean(b: => Boolean) {
    def ==>(p: => Prop) = Prop(b) ==> p
  }

  def apply(b: Boolean): Prop = ???

  implicit def BooleanOperators(b: => Boolean): ExtendedBoolean = new ExtendedBoolean(b)

  implicit def propBoolean(b: Boolean): Prop = Prop(b)

}

import Prop._

class SCL8643 {

  false <ref>==> false
}