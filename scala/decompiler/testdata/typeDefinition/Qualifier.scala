package typeDefinition

trait Qualifier {
  private[typeDefinition] class PrivateClass

  protected[typeDefinition] class ProtectedClass

  private[typeDefinition] trait PrivateTrait

  protected[typeDefinition] trait ProtectedTrait

  private[typeDefinition] object PrivateObject

  protected[typeDefinition] object ProtectedObject

  object Object {
    private[Object] class Class
  }
}