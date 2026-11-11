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

  protected[this] def protectedThisDef: Int = ???

  private[member] val privateVal: Int = ???

  protected[member] val protectedVal: Int = ???

  protected[this] val protectedThisVal: Int = ???

  private[member] var privateVar: Int = ???

  protected[member] var protectedVar: Int = ???

  protected[this] var protectedThisVar: Int = ???

  private[member] type PrivateAbstractType

  protected[member] type ProtectedAbstractType

  protected[this] type ProtectedThisAbstractType

  private[member] type PrivateTypeAlias = Int

  protected[member] type ProtectedTypeAlias = Int

  protected[this] type ProtectedThisTypeAlias = Int

  extension (i: Int)
    private[member] def privateExtensionMethod: Unit = ???

    protected[member] def protectedExtensionMethod: Unit = ???

    protected[this] def protectedThisExtensionMethod: Unit = ???

  trait T1

  trait T2

  private[member] given privateGivenAlias: T1 = ???

  private[member] given T1 = ???

  private[member] given privateGivenInstance: T1 with {}

  private[member] given T2 with {}

  object Object {
    private/**/[Object]/**/ def method: Int = ???
  }
}