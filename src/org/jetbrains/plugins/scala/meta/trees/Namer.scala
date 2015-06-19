package org.jetbrains.plugins.scala.meta.trees

import com.intellij.psi.PsiElement

import scala.meta.internal.ast.Term.Param
import scala.{Seq => _}
import scala.collection.immutable.Seq
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.{api => p}
import org.jetbrains.plugins.scala.lang.psi.{impl => impl}
import org.jetbrains.plugins.scala.lang.psi.{types => ptype}
import scala.meta.internal.{ast=>m}
import scala.meta.internal.{semantic => h}

trait Namer {
  self: Converter =>

  def toTermName(elem: PsiElement): m.Term.Name = elem match {
    case td: p.toplevel.typedef.ScTemplateDefinition =>
      m.Term.Name(td.name).withDenot(td)
    case ne: p.toplevel.ScNamedElement =>
      m.Term.Name(ne.name).withDenot(ne)
    case re: p.expr.ScReferenceExpression =>
      toTermName(re.resolve())
    case se: impl.toplevel.synthetic.SyntheticNamedElement => ??? // FIXME: find a way to resolve synthetic elements
    case other => other ?!
  }

  def toTypeName(elem: PsiElement): m.Type.Name = elem match {
    case ta: p.statements.ScTypeAlias => m.Type.Name(ta.name).withDenot(ta)
  }

  def toName(td: p.toplevel.typedef.ScTypeDefinition) = {
    m.Type.Name(td.name)
  }

  def toName(o: p.toplevel.typedef.ScObject) = {
    m.Term.Name(o.name)
  }

  def toName(n: p.toplevel.ScNamedElement) = {
    m.Term.Name(n.name)
  }

  def toName(t: p.base.ScPrimaryConstructor) = {
    m.Ctor.Ref.Name("this")
  }

  def toName(t: p.base.ScConstructor) = {
    m.Ctor.Ref.Name(t.reference.get.qualName)
  }

  def ind(cr: p.base.ScStableCodeReferenceElement): m.Name.Indeterminate = {
    m.Name.Indeterminate(cr.getCanonicalText)
  }

  def ref(cr: p.base.ScStableCodeReferenceElement): m.Term.Name = {
    m.Term.Name(cr.refName).withDenot(cr)
  }
  
}
