package typeDefinition

trait Modifiers {
  protected class ProtectedClass

  abstract class AbstractClass

  final class FinalClass

  sealed class SealedClass

  case class CaseClass()

  protected trait ProtectedTrait

  sealed trait SealedTrait

  private object PrivateObject

  protected object ProtectedObject

  implicit object ImplicitObject

  case object CaseObject

  trait A {
    protected def x: Any
  }

  trait B extends A {
    abstract override protected implicit case object x
  }

  protected sealed abstract class C
}