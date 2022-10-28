package parameter

trait Qualifier {
  class ClassPrivate(private[parameter] val x: Int)

  class ClassProtected(protected[parameter] val x: Int)

  class TraitPrivate(private[parameter] val x: Int)

  class TraitProtected(protected[parameter] val x: Int)

  object Object {
    class Class(private[Object] val x: Int)
  }
}