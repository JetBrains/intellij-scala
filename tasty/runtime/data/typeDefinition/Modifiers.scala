package typeDefinition

trait Modifiers {
  private class PrivateClass

  protected class ProtectedClass

  abstract class AbstractClass

  final class FinalClass

  sealed class SealedClass

  open class OpenClass

  case class CaseClass()

  private trait PrivateTrait

  protected trait ProtectedTrait

  abstract trait AbstractTrait

  sealed trait SealedTrait

  open trait OpenTrait

  private object PrivateObject

  protected object ProtectedObject

  implicit object ImplicitObject

  case object CaseObject

  private enum PrivateEnum {
    case Case
  }

  protected enum ProtectedEnum {
    case Case
  }

  enum PrivateEnumCaseObject {
    private case Object
  }

  enum ProtectedEnumCaseObject {
    protected case Object
  }

  enum PrivateEnumCaseClass {
    private case Class()
  }

  enum ProtectedEnumCaseClass {
    protected case Class()
  }
}