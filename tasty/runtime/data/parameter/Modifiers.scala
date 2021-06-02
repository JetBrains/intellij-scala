package parameter

trait Modifiers {
  class ClassUsing(using x: Int, y: Long)

  class ClassImplicit(implicit x: Int, y: Long)

  class ClassVal(val x: Int)

  class ClassVar(var x: Int)

  class ClassPrivate(private val x: Int)

  class ClassProtected(protected val x: Int)

  class ClassFinal(final val x: Int)

  class ClassOverride(override val hashCode: Int)

  trait TraitUsing(using x: Int, y: Long)

  trait TraitImplicit(implicit x: Int, y: Long)

  class TraitVal(val x: Int)

  class TraitVar(var x: Int)

  class TraitPrivate(private val x: Int)

  class TraitProtected(protected val x: Int)

  class TraitFinal(final val x: Int)

  class TraitOverride(override val hashCode: Int)

  def defUsing(using x: Int, y: Long): Unit

  def defImplicit(implicit x: Int, y: Long): Unit

  enum EnumVal(val x: Int) {
    case Case extends EnumVal/**/(1)/**/
  }

  enum EnumVar(var x: Int) {
    case Case extends EnumVar/**/(1)/**/
  }

  enum EnumPrivate(private val x: Int) {
    case Case extends EnumPrivate/**/(1)/**/
  }

  enum EnumProtected(protected val x: Int) {
    case Case extends EnumProtected/**/(1)/**/
  }

  enum EnumFinal(final val x: Int) {
    case Case extends EnumFinal/**/(1)/**/
  }

  enum EnumOverride(override val hashCode: Int) {
    case Case extends EnumOverride/**/(1)/**/
  }

  enum EnumCaseClassVal {
    case Class(/**/val /**/x: Int)
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