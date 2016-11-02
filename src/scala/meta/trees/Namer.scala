package scala.meta.trees

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiClass, PsiElement, PsiMethod, PsiPackage}
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.api.StdType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaTypeVisitor}
import org.jetbrains.plugins.scala.lang.psi.{impl, api => p, types => ptype}
import org.jetbrains.plugins.scala.lang.resolve.ResolvableReferenceElement

import scala.language.postfixOps
//import scala.meta.internal.ast.Type
import scala.meta.internal.{semantic => h}
import scala.{meta=>m}
import scala.meta.trees.error._
import scala.{Seq => _}

trait Namer {
  self: TreeConverter =>

  class SyntheticException extends Throwable

  def toTermName(elem: PsiElement, insertExpansions: Boolean = true): m.Term.Name = {
    ProgressManager.checkCanceled()
    elem match {
      // TODO: what to resolve apply/update methods to?
      case sf: ScFunction if insertExpansions && (sf.name == "apply" || sf.name == "update" || sf.name == "unapply") =>
        toTermName(sf.containingClass)
      case sf: ScFunction =>
        m.Term.Name(sf.name)
      case ne: ScNamedElement =>
        m.Term.Name(ne.name)
      case cr: ScReferenceElement if dumbMode =>
        m.Term.Name(cr.refName)
      case cr: ResolvableReferenceElement  =>
        cr.bind()  match {
          case Some(x) => try {
            toTermName(x.element)
          } catch {
            case _: SyntheticException =>
              elem.getContext match {
                case mc: ScSugarCallExpr => mkSyntheticMethodName(toType(mc.getBaseExpr.getType()), x.getElement.asInstanceOf[ScSyntheticFunction], mc)
                case other => other ???
              }
          }
          case None => throw new ScalaMetaResolveError(cr)
        }
      case se: impl.toplevel.synthetic.SyntheticNamedElement =>
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
    case re: ScReferenceElement if dumbMode =>
      m.Type.Name(re.refName)
    case re: ResolvableReferenceElement =>
      toTypeName(re.resolve())
    case sc: impl.toplevel.synthetic.ScSyntheticClass =>
      m.Type.Name(sc.className)
    case se: impl.toplevel.synthetic.SyntheticNamedElement =>
      die(s"Synthetic elements not implemented") // FIXME: find a way to resolve synthetic elements
    case _: PsiPackage | _: ScObject =>
      unreachable(s"Package and Object types shoud be Singleton, not Name: ${elem.getText}")
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
      override def visitStdType(x: StdType) = {
        res = x match {
          case ptype.api.Any    => std.anyTypeName
          case ptype.api.AnyRef => std.anyRefTypeName
          case ptype.api.AnyVal => std.anyValTypeName
          case ptype.api.Nothing=> std.nothingTypeName
          case ptype.api.Null   => std.nullTypeName
          case ptype.api.Unit   => std.unit
          case ptype.api.Boolean=> std.boolean
          case ptype.api.Char   => std.char
          case ptype.api.Int    => std.int
          case ptype.api.Float  => std.float
          case ptype.api.Double => std.double
          case ptype.api.Byte   => std.byte
          case ptype.api.Short  => std.short
          case _ =>
            val clazz = ScalaPsiManager.instance(getCurrentProject).getCachedClass(GlobalSearchScope.allScope(getCurrentProject), s"scala.${x.name}")
            if (clazz != null)
              m.Type.Name(x.name)
            else null
        }
      }
    }
    tp.visitType(visitor)
    if (res != null) res else die(s"failed to convert type $tp")
  }

  def toCtorName(c: ScStableCodeReferenceElement): m.Ctor.Ref.Name = {
    // FIXME: what about other cases of m.Ctor ?
    val resolved = toTermName(c)
    resolved match {
      case n@m.Term.Name(value) =>
        m.Ctor.Ref.Name(value)
      case other => unreachable
    }
  }

  def toParamName(param: Parameter): m.Term.Param.Name = {
    m.Term.Name(param.name) // TODO: param denotation
  }

  def toPrimaryCtorName(t: ScPrimaryConstructor) = {
    m.Ctor.Ref.Name("this")
  }

  def ind(cr: ScStableCodeReferenceElement): m.Name.Indeterminate = {
    m.Name.Indeterminate(cr.qualName)
  }

  // only used in m.Term.Super/This
  def ind(td: ScTemplateDefinition): m.Name.Indeterminate = {
    m.Name.Indeterminate(td.name)
  }

  // only raw type names can be used as super selector
  def getSuperName(tp: ScSuperReference): m.Name.Qualifier = {
    def loop(mtp: m.Type): m.Name.Qualifier = {
      mtp match {
        case n@m.Type.Name(value) => m.Name.Indeterminate(value)
        case _: m.Type.Select => loop(mtp.stripped)
        case _: m.Type.Project => loop(mtp.stripped)
        case other => throw new AbortException(other, "Super selector cannot be non-name type")
      }
    }
    tp.staticSuper.map(t=>loop(toType(t))).getOrElse(m.Name.Anonymous())
  }

  // FIXME: everything
  def ctorParentName(tpe: types.ScTypeElement) = {
    val raw = toType(tpe)
    raw.stripped match {
      case n@m.Type.Name(value) =>
        m.Ctor.Ref.Name(value)
      case other => die(s"Unexpected type in parents: $other")
    }
  }

}
