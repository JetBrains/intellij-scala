package scala.meta.trees

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiClass, PsiElement, PsiMethod, PsiPackage}
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.api.StdType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaTypeVisitor}

import scala.annotation.tailrec
import scala.language.postfixOps
import scala.meta.ScalaMetaBundle
import scala.meta.trees.error._
import scala.{meta => m, Seq => _}

trait Namer {
  self: TreeConverter =>

  class SyntheticException extends Throwable

  def toTermName(elem: PsiElement, insertExpansions: Boolean = false): m.Term.Name = {
    ProgressManager.checkCanceled()
    elem match {
      // TODO: what to resolve apply/update methods to?
      case sf: ScFunction if insertExpansions && (sf.name == "apply" || sf.name == "update" || sf.name == "unapply") =>
        toTermName(sf.containingClass)
      case sf: ScFunction =>
        m.Term.Name(sf.name)
      case ne: ScNamedElement =>
        m.Term.Name(ne.name)
      case cr: ScReference if dumbMode =>
        m.Term.Name(cr.refName)
      case cr: ScReference  =>
        cr.bind()  match {
          case Some(x) => try {
            toTermName(x.element)
          } catch {
            case _: SyntheticException =>
              elem.getContext match {
                case mc: ScSugarCallExpr => mkSyntheticMethodName(toType(mc.getBaseExpr.`type`()), x.getElement.asInstanceOf[ScSyntheticFunction], mc)
                case other => other ???
              }
          }
          case None => throw new ScalaMetaResolveError(cr)
        }
      case _: impl.toplevel.synthetic.SyntheticNamedElement =>
        throw new SyntheticException
      //    case cs: ScConstructor =>
      //      toTermName(cs.reference.get)
      // Java stuff starts here
      case pp: PsiPackage =>
        m.Term.Name(pp.getName)
      case pc: PsiClass =>
        m.Term.Name(pc.getName)
      case pm: PsiMethod =>
        m.Term.Name(pm.getName)
      case other => other ?!
    }
  }

  def toTypeName(elem: PsiElement): m.Type.Name = elem match {
    case ne: ScNamedElement =>
      m.Type.Name(ne.name)
    case re: ScReference if dumbMode =>
      m.Type.Name(re.refName)
    case re: ScReference =>
      toTypeName(re.resolve())
    case sc: impl.toplevel.synthetic.ScSyntheticClass =>
      m.Type.Name(sc.className)
    case _: impl.toplevel.synthetic.SyntheticNamedElement =>
      die(ScalaMetaBundle.message("synthetic.elements.not.implemented")) // FIXME: find a way to resolve synthetic elements
    case _: PsiPackage | _: ScObject =>
      unreachable(ScalaMetaBundle.message("package.and.object.types.should.be.singleton.not.name...", elem.getText))
    // Java stuff starts here
    case pc: PsiClass =>
      m.Type.Name(pc.getName)
    case pm: PsiMethod =>
      m.Type.Name(pm.getName)
    case other => other ?!
  }

  def toStdTypeName(tp: ScType): m.Type.Name = {
    var res: m.Type.Name = null
    val visitor = new ScalaTypeVisitor {
      override def visitStdType(x: StdType): Unit = {
        val stdTypes = x.projectContext.stdTypes
        import stdTypes._

        res = x match {
          case Any    => std.anyTypeName
          case AnyRef => std.anyRefTypeName
          case AnyVal => std.anyValTypeName
          case Nothing=> std.nothingTypeName
          case Null   => std.nullTypeName
          case Unit   => std.unit
          case Boolean=> std.boolean
          case Char   => std.char
          case Int    => std.int
          case Float  => std.float
          case Double => std.double
          case Byte   => std.byte
          case Short  => std.short
          case _ =>
            val clazz = ScalaPsiManager.instance(getCurrentProject).getCachedClass(GlobalSearchScope.allScope(getCurrentProject), s"scala.${x.name}")
            if (clazz != null)
              m.Type.Name(x.name)
            else null
        }
      }
    }
    tp.visitType(visitor)
    if (res != null) res else die(ScalaMetaBundle.message("failed.to.convert.type.tp", tp))
  }

  def toParamName(param: Parameter): m.Term.Name = {
    m.Term.Name(param.name)
  }

  def toPrimaryCtorName: m.Name =
    m.Name.Anonymous()

  def ind(cr: ScStableCodeReference): m.Name.Indeterminate = {
    m.Name.Indeterminate(cr.qualName)
  }

  // only used in m.Term.Super/This
  def ind(td: ScTemplateDefinition): m.Name.Indeterminate = {
    m.Name.Indeterminate(td.name)
  }

  // only raw type names can be used as super selector
  def getSuperName(tp: ScSuperReference): m.Name = {
    @tailrec
    def loop(mtp: m.Type): m.Name = {
      mtp match {
        case m.Type.Name(value) => m.Name.Indeterminate(value)
        case _: m.Type.Select => loop(mtp.stripped)
        case _: m.Type.Project => loop(mtp.stripped)
        case other => throw new AbortException(other, ScalaMetaBundle.message("super.selector.cannot.be.non.name.type"))
      }
    }
    tp.staticSuper.map(t=>loop(toType(t))).getOrElse(m.Name.Anonymous())
  }

}
