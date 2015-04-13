package org.jetbrains.plugins.scala.meta.trees

import scala.meta.internal.ast.Term.Param
import scala.{Seq => _}
import scala.collection.immutable.Seq
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.{api => p}
import org.jetbrains.plugins.scala.lang.psi.{types => ptype}
import scala.meta.internal.{ast=>m}
import scala.meta.internal.{hygiene => h}

trait Namer {
  self: Converter =>

  def toName(e: p.expr.ScExpression): m.Term.Name = {
    m.Term.Name(e.getText)
  }

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

  def ind(cr: p.base.ScStableCodeReferenceElement): m.Name.Indeterminate = {
    m.Name.Indeterminate(cr.getCanonicalText)
  }

  def ref(cr: p.base.ScStableCodeReferenceElement): m.Term.Name = {
    m.Term.Name(cr.getCanonicalText)
  }
  
}
