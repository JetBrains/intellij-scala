package member

trait Modifiers {
  class PrivatePrimaryConstructor private ()

  class PrivatePrimaryConstructorParameter private (/**/x: Int/**/)

  class PrivatePrimaryConstructorParameters private (/**/x: Int, y: Int/**/)

  class PrivatePrimaryConstructorValParameter private (val x: Int)

  case class PrivatePrimaryConstructorCaseClassParameter private (x: Int)

  class PrivatePrimaryConstructorPrivateValParameter private (/**/private val x: Int/**/)

  class PrivatePrimaryConstructorImplicitParameter private (/**/implicit x: Int/**/)

  class PrivatePrimaryConstructorImplicitParameters private (/**/implicit x: Int, y: Int/**/)

  class PrivatePrimaryConstructorImplicitVal1 private (implicit val x: Int/**/, y: Int/**/)

  class PrivatePrimaryConstructorImplicitVal2 private (/**/x: Int, /**/implicit val y: Int)

  class PrivatePrimaryConstructorImplicitVal3 private (implicit /**/x: Int, /**/val y: Int)

  class PrivatePrimaryConstructorImplicitVal4 private (implicit val x: Int, val y: Int)

  class PrivatePrimaryConstructorImplicitPrivateVal1 private (/**/implicit private val x: Int, y: Int/**/)

  class PrivatePrimaryConstructorImplicitPrivateVal2 private (/**/x: Int, private implicit val y: Int/**/)

  class PrivatePrimaryConstructorImplicitPrivateVal3 private (/**/implicit x: Int, private val y: Int/**/)

  class PrivatePrimaryConstructorImplicitPrivateVal4 private (/**/implicit private val x: Int, private val y: Int/**/)

  class PrivatePrimaryConstructorContextBound[A/**/: Ordering/**/] private ()

  class ProtectedPrimaryConstructor protected ()

  class PrivatePrimaryConstructorTypeParameter[A] private ()

  class ProtectedPrimaryConstructorTypeParameter[A] protected ()

  class PrivateAuxilliaryConstructor/**/ {
    private def this(x: Int) = this()
  }/**/

  class ProtectedAuxilliaryConstructor {
    protected def this(x: Int) = /**/this()/*???*/
  }
/**/
  private def privateDef: Int = ???
/**/
  protected def protectedDef: Int = ???

  final def finalDef: Int = ???

  implicit def implicitDef: Int = ???

  override def hashCode(): Int = ???
/**/
  private val privateVal: Int = ???
/**/
  protected val protectedVal: Int = ???

  final val finalVal: Int = ???

  implicit val implicitVal: Int = ???

  lazy val lazyVal: Int = ???

  override val toString: String = ???
/**/
  private var privateVar: Int = ???
/**/
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