package member

trait Modifiers {
  class PrivatePrimaryConstructor private ()

  class ProtectedPrimaryConstructor protected ()

  class PrivateAuxilliaryConstructor(x: Int) {
    private def this(x: Int, y: Int) = this(???)
  }

  class ProtectedAuxilliaryConstructor(x: Int) {
    protected def this(x: Int, y: Int) = this(???)
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
}