package member

trait Modifiers {
  class PrivatePrimaryConstructor private ()

  class ProtectedPrimaryConstructor protected ()

  class PrivatePrimaryConstructorTypeParameter[A] private ()

  class ProtectedPrimaryConstructorTypeParameter[A] protected ()

  class PrivateAuxilliaryConstructor {
    private def this(x: Int) = /**/this()/*???*/
  }

  class ProtectedAuxilliaryConstructor {
    protected def this(x: Int) = /**/this()/*???*/
  }

  private def privateDef: Int = ???

  protected def protectedDef: Int = ???

  final def finalDef: Int = ???

  implicit def implicitDef: Int = ???

  override def hashCode(): Int = ???

  private val privateVal: Int = ???

  protected val protectedVal: Int = ???

  final val finalVal: Int = ???

  implicit val implicitVal: Int = ???

  lazy val lazyVal: Int = ???

  override val toString: String = ???

  private var privateVar: Int = ???

  protected var protectedVar: Int = ???

  final var finalVar: Int = ???

  implicit var implicitVar: Int = ???

  private type PrivateAbstractType

  protected type ProtectedAbstractType

  final type FinalAbstractType

  private type PrivateTypeAlias = Int

  protected type ProtectedTypeAlias = Int

  final type FinalTypeAlias = Int

  trait A {
    protected def x: Int
  }

  trait B extends A {
    abstract override protected implicit final def x: Int = ???
  }

  opaque type OpaqueTypeAlias = /**/Int/*???*/

  extension (i: Int)
    private def privateExtensionMethod: Unit = ???

  extension (i: Int)
    protected def protectedExtensionMethod: Unit = ???

  extension (i: Int)
    final def finalExtensionMethod: Unit = ???

  trait T1

  trait T2

  private given privateGivenAlias: T1 = ???

  private given T1 = ???

  private given privateGivenInstance: T1 with {}

  private given T2 with {}
}