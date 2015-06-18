package org.jetbrains.plugins.scala.meta.trees

import com.intellij.psi.PsiElement

import scala.meta.internal.ast.Term.Param
import scala.{Seq => _}
import scala.collection.immutable.Seq
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.{api => p}
import org.jetbrains.plugins.scala.lang.psi.{types => ptype}
import scala.meta.internal.{ast=>m}
import scala.meta.internal.{semantic => h}

trait Namer {
  self: Converter =>

  def toName(elem: PsiElement): m.Term.Name = {
    elem match {
      case td: p.toplevel.typedef.ScTemplateDefinition => m.Term.Name(td.name).withDenot(td)
      case ne: p.toplevel.ScNamedElement => m.Term.Name(ne.name).withDenot(ne)
      case re: p.expr.ScReferenceExpression => toName(re.resolve())
    }
  }

//  def toName(e: p.expr.ScExpression): m.Term.Name = {
//    m.Term.Name(e.getText)
//  }

  def toName(e: p.statements.ScTypeAlias): m.Type.Name = {
    m.Type.Name(e.name)
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
