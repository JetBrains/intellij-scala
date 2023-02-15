package parameter

trait Modifiers {
  class ClassUsing(using x: Int, y: Long)

  class ClassAnonymousUsing(using Int, Long)

  class ClassUsingVal(using val x: Int, val y: Int)

  class ClassRegularAndUsing(x: Int)(using y: Long)

  class ClassUsingMultiple(using x: Int)(using y: Long)

  class ClassImplicit(implicit x: Int, y: Long)

  class ClassImplicitVal1(implicit val x: Int, val y: Int)

  class ClassImplicitVal2(val x: Int, implicit val y: Int)

  class ClassRegularAndImplicit(x: Int)(implicit y: Long)

  class ClassUsingAndImplicit(using x: Int)(implicit y: Long)

  class ClassUsingImplicitVal1(using implicit val x: Int, val y: Int)

  class ClassUsingImplicitVal2(using val x: Int, implicit val y: Int)

  class ClassUsingImplicitVal3(using implicit val x: Int, implicit val y: Int)

  class ClassVal(val x: Int)

  class ClassVar(var x: Int)

  class ClassPrivate(/**/private val /**/x: Int)

  class ClassProtected(protected val x: Int)

  class ClassFinal(final val x: Int)

  class ClassOverride(override val hashCode: Int)

  trait A {
    protected val x: Int
  }

  class B(implicit override protected final val x: Int) extends A

  trait TraitUsing(using x: Int, y: Long)

  trait TraitAnonymousUsing(using Int, Long)

  trait TraitUsingVal(using val x: Int, val y: Long)

  trait TraitRegularAndUsing(x: Int)(using y: Long)

  trait TraitUsingMultiple(using x: Int)(using y: Long)

  trait TraitImplicit(implicit x: Int, y: Long)

  trait TraitImplicitVal(implicit val x: Int, val y: Int)

  trait TraitRegularAndImplicit(x: Int)(implicit y: Long)

  trait TraitUsingAndImplicit(using x: Int)(implicit y: Long)

  class TraitVal(val x: Int)

  class TraitVar(var x: Int)

  class TraitPrivate(/**/private val /**/x: Int)

  class TraitProtected(protected val x: Int)

  class TraitFinal(final val x: Int)

  class TraitOverride(override val hashCode: Int)

  def defUsing(using x: Int, y: Long): Unit

  def defUsingAnonymous(using Int, Long): Unit

  def defRegularAndUsing(x: Int)(using y: Long): Unit

  def defUsingMultiple(using x: Int)(using y: Long): Unit

  def defAnonymousUsing(using Int, Long): Unit

  def defImplicit(implicit x: Int, y: Long): Unit

  def defRegularAndImplicit(x: Int)(implicit y: Long): Unit

  def defUsingAndImplicit(using x: Int)(implicit y: Long): Unit

  def defContextBoundAndAnonymousUsing[A: Ordering](using Int): Unit

  enum EnumVal(val x: Int) {
    case Case/**/ extends EnumVal(1)/**/
  }

  enum EnumVar(var x: Int) {
    case Case/**/ extends EnumVar(1)/**/
  }

  enum EnumPrivate(/**/private val /**/x: Int) {
    case Case/**/ extends EnumPrivate(1)/**/
  }

  enum EnumProtected(protected val x: Int) {
    case Case/**/ extends EnumProtected(1)/**/
  }

  enum EnumFinal(final val x: Int) {
    case Case/**/ extends EnumFinal(1)/**/
  }

  enum EnumOverride(override val hashCode: Int) {
    case Case/**/ extends EnumOverride(1)/**/
  }

  enum EnumCaseClassVal {
    case Class(/**/val /**/x: Int)
  }

  enum EnumCaseClassVar {
    case Class(var x: Int)
  }

  enum EnumCaseClassPrivate {
    case Class(/**/private val /**/x: Int)
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

  extension (i: Int)
    def extensionMethodImplicit(implicit x: Int, y: Int): Unit = ???
}