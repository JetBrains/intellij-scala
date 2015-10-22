package scala.meta.trees

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiClass, PsiElement, PsiMethod, PsiPackage}
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaTypeVisitor, StdType}
import org.jetbrains.plugins.scala.lang.psi.{api => p, impl, types => ptype}

import scala.language.postfixOps
import scala.meta.internal.{ast => m, semantic => h}
import scala.{Seq => _}

trait Namer {
  self: TreeConverter =>

  def toTermName(elem: PsiElement): m.Term.Name = elem match {
      // TODO: what to resolve apply/update methods to?
    case sf: ScFunction if sf.name == "apply" || sf.name == "update" =>
      m.Term.Name(sf.containingClass.name).withDenot(sf)
    case sf: ScFunction =>
      m.Term.Name(sf.name).withDenot(sf)
    case ne: ScNamedElement =>
      m.Term.Name(ne.name).withDenot(ne)
    case re: ScReferenceExpression =>
      toTermName(re.resolve())
    case cr: ScStableCodeReferenceElement =>
      m.Term.Name(cr.refName).withDenot(cr)
    case se: impl.toplevel.synthetic.SyntheticNamedElement =>
      throw new ScalaMetaException(s"Synthetic elements not implemented") // FIXME: find a way to resolve synthetic elements
    case cs: ScConstructor =>
      toTermName(cs.reference.get)
    // Java stuff starts here
    case pp: PsiPackage =>
      m.Term.Name(pp.getName).withDenot(pp)
    case pc: PsiClass =>
      m.Term.Name(pc.getName).withDenot(pc)
    case pm: PsiMethod =>
      m.Term.Name(pm.getName).withDenot(pm)
    case other => other ?!
  }

  def toTypeName(elem: PsiElement): m.Type.Name = elem match {
    case ne: ScNamedElement =>
      m.Type.Name(ne.name).withDenot(ne).setTypechecked
    case re: ScReferenceExpression =>
      toTypeName(re.resolve())
    case sc: impl.toplevel.synthetic.ScSyntheticClass =>
      m.Type.Name(sc.className).withDenot(sc).setTypechecked
    case cr: ScStableCodeReferenceElement =>
      toTypeName(cr.resolve())
    case se: impl.toplevel.synthetic.SyntheticNamedElement =>
      throw new ScalaMetaException(s"Synthetic elements not implemented") // FIXME: find a way to resolve synthetic elements
    case other => other ?!
  }

  def toTypeName(tp: ScType): m.Type.Name = {
    var res: m.Type.Name = null
    val visitor = new ScalaTypeVisitor {
      override def visitStdType(x: StdType) = {
        val clazz = ScalaPsiManager.instance(getCurrentProject).getCachedClass(GlobalSearchScope.allScope(getCurrentProject), s"scala.${x.name}")
        res = m.Type.Name(x.name).withDenot(clazz)
      }
    }
    tp.visitType(visitor)
    if (res != null) res else throw new ScalaMetaException(s"failed to convert type $tp")
  }

  def toCtorName(c: ScConstructor): m.Term.Ref = {
    // FIXME: what about other cases of m.Ctor ?
    val resolved = toTermName(c)
    resolved match {
      case n@m.Term.Name(value) =>
        m.Ctor.Ref.Name(value).withAttrs(n.denot, typingLike = toType(c))
      case other => unreachable
    }
  }

  def toParamName(param: Parameter): m.Term.Param.Name = {
    m.Term.Name(param.name).withAttrs(h.Denotation.Zero).setTypechecked // TODO: param denotation
  }

  def toPrimaryCtorName(t: ScPrimaryConstructor) = {
    m.Ctor.Ref.Name("this")
  }

  def ind(cr: ScStableCodeReferenceElement): m.Name.Indeterminate = {
    m.Name.Indeterminate(cr.getCanonicalText)
  }

}
