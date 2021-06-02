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

  enum EnumVal(val x: Int) {
    case Case extends EnumVal(???)
  }

  enum EnumVar(var x: Int) {
    case Case extends EnumVar(???)
  }

  enum EnumPrivate(private val x: Int) {
    case Case extends EnumPrivate(???)
  }

  enum EnumProtected(protected val x: Int) {
    case Case extends EnumProtected(???)
  }

  enum EnumFinal(final val x: Int) {
    case Case extends EnumFinal(???)
  }

  enum EnumOverride(override val hashCode: Int) {
    case Case extends EnumOverride(???)
  }

  enum EnumCaseClassVal {
    case Class(x: Int)
  }

  enum EnumCaseClassVar {
    case Class(var x: Int)
  }

  enum EnumCaseClassPrivate {
    case Class(private val x: Int)
  }

  enum EnumCaseClassProtected {
    case Class(protected val x: Int)
  }

  enum EnumCaseClassFinal {
    case Class(final val x: Int)
  }

  enum EnumCaseClassOverride {
    case Class(override val hashCode: Int)
  }
}