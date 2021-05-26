package parameter

trait Modifiers {
  class ClassUsing(using x: Int, y: Int)

  class ClassImplicit(implicit x: Int, y: Int)

  class ClassVal(val x: Int)

  class ClassVar(var x: Int)

  class ClassPrivate(private val x: Int)

  class ClassProtected(protected val x: Int)

  class ClassFinal(final val x: Int)

  class ClassOverride(override val hashCode: Int)

  trait TraitUsing(using x: Int, y: Int)

  trait TraitImplicit(implicit x: Int, y: Int)

  class TraitVal(val x: Int)

  class TraitVar(var x: Int)

  class TraitPrivate(private val x: Int)

  class TraitProtected(protected val x: Int)

  class TraitFinal(final val x: Int)

  class TraitOverride(override val hashCode: Int)

  def defUsing(using x: Int, y: Int): Unit

  def defImplicit(implicit x: Int, y: Int): Unit
}