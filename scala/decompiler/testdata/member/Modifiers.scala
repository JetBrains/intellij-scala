package member

trait Modifiers {
  class PrivatePrimaryConstructor private ()

  class ProtectedPrimaryConstructor protected ()

  class PrivatePrimaryConstructorTypeParameter[A] private ()

  class ProtectedPrimaryConstructorTypeParameter[A] protected ()

  class ProtectedAuxilliaryConstructor {
    protected def this(x: Int) = /**/this()/*???*/
  }

  protected def protectedDef: Int = ???

  final def finalDef: Int = ???

  implicit def implicitDef: Int = ???

  override def hashCode(): Int = ???

  protected val protectedVal: Int = ???

  final val finalVal: Int = ???

  implicit val implicitVal: Int = ???

  lazy val lazyVal: Int = ???

  override val toString: String = ???

  protected var protectedVar: Int = ???

  final var finalVar: Int = ???

  implicit var implicitVar: Int = ???

  protected type ProtectedAbstractType

  private type PrivateTypeAlias = Int

  protected type ProtectedTypeAlias = Int

  final type FinalTypeAlias = Int

  trait A {
    protected def x: Int
  }

  trait B extends A {
    abstract override protected implicit final def x: Int = ???
  }
}