package member

trait Modifiers {
  class PrivatePrimaryConstructor private ()

  class PrivatePrimaryConstructorParameter private (/**/x: Int/**/)

  class PrivatePrimaryConstructorParameters private (/**/x: Int, y: Int/**/)

  class PrivatePrimaryConstructorValParameter private (val x: Int)

  case class PrivatePrimaryConstructorCaseClassParameter private (x: Int)

  class PrivatePrimaryConstructorPrivateValParameter private (/**/private val x: Int/**/)

  class PrivatePrimaryConstructorUsingParameter private (/**/using x: Int/**/)

  class PrivatePrimaryConstructorUsingParameters private (/**/using x: Int, y: Int/**/)

  class PrivatePrimaryConstructorUsingVal1 private (using val x: Int/**/, y: Int/**/)

  class PrivatePrimaryConstructorUsingVal3 private (using /**/x: Int, /**/val y: Int)

  class PrivatePrimaryConstructorUsingPrivateVal1 private (/**/using private val x: Int, y: Int/**/)

  class PrivatePrimaryConstructorUsingPrivateVal3 private (/**/using x: Int, private val y: Int/**/)

  class PrivatePrimaryConstructorUsingPrivateVal4 private (/**/using private val x: Int, private val y: Int/**/)

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

  class PrivatePrimaryConstructorUsingImplicitVal1 private (using implicit val x: Int/**/, y: Int/**/)

  class PrivatePrimaryConstructorUsingImplicitVal2 private (using /**/x: Int, /**/implicit val y: Int)

  class PrivatePrimaryConstructorUsingImplicitVal4 private (using implicit val x: Int, implicit val y: Int)

  class PrivatePrimaryConstructorUsingImplicitPrivateVal1 private (/**/using private implicit val x: Int, y: Int/**/)

  class PrivatePrimaryConstructorUsingImplicitPrivateVal2 private (/**/using x: Int, private implicit val y: Int/**/)

  class PrivatePrimaryConstructorUsingImplicitPrivateVal4 private (/**/using private implicit val x: Int, private implicit val y: Int/**/)

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
/**/
  private type PrivateAbstractType1

  private type PrivateAbstractType2[A]
/**/
  protected type ProtectedAbstractType

  final type FinalAbstractType

  private type PrivateTypeAlias1 = Public1

  private type PrivateTypeAlias2 = Public2[Int]

  private type PrivateTypeAlias3[A] = Public2[A]

  class Public1

  class Public2[A]
/**/
  private type PrivateTypeAliasForPrivate1 = Private1

  private type PrivateTypeAliasForPrivate2 = Private2[Int]

  private type PrivateTypeAliasForPrivate3[A] = Private2[A]

  private class Private1

  private class Private2[A]
/**/
  protected type ProtectedTypeAlias = Int

  final type FinalTypeAlias = Int

  trait A {
    protected def x: Int
  }

  trait B extends A {
    abstract override protected implicit final def x: Int = ???
  }

  opaque type OpaqueTypeAlias = /**/Int/*???*/
/**/
  extension (i: Int)
    private def privateExtensionMethod: Unit = ???
/**/
  extension (i: Int)
    protected def protectedExtensionMethod: Unit = ???

  extension (i: Int)
    final def finalExtensionMethod: Unit = ???

  trait T1

  trait T2/**/

  private given privateGivenAlias: T1 = ???

  private given T1 = ???

  private given privateGivenInstance: T1 with {}

  private given T2 with {}/**/
}