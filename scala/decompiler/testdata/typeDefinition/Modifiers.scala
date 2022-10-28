package typeDefinition

trait Modifiers {
  private class PrivateClass

  protected class ProtectedClass

  abstract class AbstractClass

  final class FinalClass

  sealed class SealedClass

  case class CaseClass()

  private trait PrivateTrait

  protected trait ProtectedTrait

  abstract trait AbstractTrait

  sealed trait SealedTrait

  private object PrivateObject

  protected object ProtectedObject

  implicit object ImplicitObject

  case object CaseObject
}