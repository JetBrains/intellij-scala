package typeDefinition

trait Qualifier {
  private[typeDefinition] class PrivateClass

  protected[typeDefinition] class ProtectedClass

  private[typeDefinition] trait PrivateTrait

  protected[typeDefinition] trait ProtectedTrait

  private[typeDefinition] object PrivateObject

  protected[typeDefinition] object ProtectedObject

  private[typeDefinition] enum PrivateEnum {
    case Case
  }

  protected[typeDefinition] enum ProtectedEnum {
    case Case
  }

  enum PrivateEnumCaseObject {
    private[typeDefinition] case Object
  }

  enum ProtectedEnumCaseObject {
    protected[typeDefinition] case Object
  }

  enum PrivateEnumCaseClass {
    private[typeDefinition] case Class()
  }

  enum ProtectedEnumCaseClass {
    protected[typeDefinition] case Class()
  }

  object Object {
    private[Object] class Class
  }
}