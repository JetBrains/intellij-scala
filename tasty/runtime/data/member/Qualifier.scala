package member

trait Qualifier {
  class PrivatePrimaryConstructor private[member] ()

  class ProtectedPrimaryConstructor protected[member] ()

  class PrivatePrimaryConstructorTypeParameter[A] private[member] ()

  class ProtectedPrimaryConstructorTypeParameter[A] protected[member] ()

  class PrivateAuxilliaryConstructor {
    private[member] def this(x: Int) = /**/this()/*???*/
  }

  class ProtectedAuxilliaryConstructor {
    protected[member] def this(x: Int) = /**/this()/*???*/
  }

  private[member] def privateDef: Int = ???

  protected[member] def protectedDef: Int = ???

  private[member] val privateVal: Int = ???

  protected[member] val protectedVal: Int = ???

  private[member] var privateVar: Int = ???

  protected[member] var protectedVar: Int = ???

  private[member] type PrivateAbstractType

  protected[member] type ProtectedAbstractType

  private[member] type PrivateTypeAlias = Int

  protected[member] type ProtectedTypeAlias = Int

  extension (i: Int)
    private[member] def privateExtensionMethod: Unit = ???

  extension (i: Int)
    protected[member] def protectedExtensionMethod: Unit = ???

  trait T1

  trait T2

  private[member] given privateGivenAlias: T1 = ???

  private[member] given T1 = ???

  private[member] given privateGivenInstance: T1 with {}

  private[member] given T2 with {}

  object Object {
    private[Object] def method: Int = ???
  }
}