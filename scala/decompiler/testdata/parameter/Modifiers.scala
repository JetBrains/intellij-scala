package parameter

trait Modifiers {
  class ClassImplicit(implicit x: Int, y: Long)

  class ClassImplicitVal1(implicit val x: Int, val y: Int)

  class ClassImplicitVal2(val x: Int, implicit val y: Int)

  class ClassRegularAndImplicit(x: Int)(implicit y: Long)

  class ClassVal1(val x: Int)

  class ClassVal2(x: Int)(val y: Int)

  class ClassVar1(var x: Int)

  class ClassVar2(x: Int)(var y: Int)

  class ClassPrivate(/**/private val /**/x: Int)

  class ClassPrivateImplicit1(implicit /**/private val /**/x: Int, y: Int)

  class ClassPrivateImplicit2(x: Int, /**/private implicit val /**/y: Int)

  class ClassPrivateImplicit3(implicit x: Int, /**/private val /**/y: Int)

  class ClassProtected(protected val x: Int)

  class ClassFinal(final val x: Int)

  class ClassOverride(override val hashCode: Int)

  trait A {
    protected val x: Int
  }

  class B(implicit override protected final val x: Int) extends A

  def defImplicit(implicit x: Int, y: Long): Unit

  def defRegularAndImplicit(x: Int)(implicit y: Long): Unit
}