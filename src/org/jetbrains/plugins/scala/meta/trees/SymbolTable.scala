package org.jetbrains.plugins.scala.meta.trees

import java.util.UUID._

import com.intellij.psi.{PsiPackage, PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil

import scala.{Seq => _}
import scala.collection.immutable.Seq
import org.jetbrains.plugins.scala.lang.psi.{api => p}
import org.jetbrains.plugins.scala.lang.psi.{types => ptype}
import scala.meta.internal.{ast=>m}
import scala.meta.internal.{semantic => h}

trait SymbolTable {
  self: Converter =>


  def isLocal(elem: PsiElement): Boolean = {
    elem match {
      case e: p.toplevel.typedef.ScTypeDefinition => e.isLocal
      case _ => false
    }
  }

  def toSymbol(elem: PsiElement): h.Symbol = {
    elem match {
      case _ if isLocal(elem) => h.Symbol.Local(randomUUID().toString)
      case td: p.toplevel.typedef.ScTypeDefinition if !td.qualifiedName.contains(".") => // empty package defn
        h.Symbol.Global(h.Symbol.Empty, td.name, h.Signature.Type)
      case td: p.toplevel.typedef.ScTypeDefinition => h.Symbol.Global(toSymbol(td.parent.get), td.name, h.Signature.Type)
      case td: p.statements.ScFunction =>
        val jvmsig = DebuggerUtil.getFunctionJVMSignature(td).getName(null)
        h.Symbol.Global(toSymbol(td.containingClass), td.name, h.Signature.Method(jvmsig))
      case pc: p.toplevel.packaging.ScPackaging => toSymbol(pc.reference.get.bind().get.element)
      case pp: PsiPackage if pp.getName == null => h.Symbol.Root
      case pc: PsiPackage => h.Symbol.Global(toSymbol(pc.getParentPackage), pc.getName, h.Signature.Type)
      case cr: p.base.ScStableCodeReferenceElement => toSymbol(cr.resolve())
      case _ => ???
    }
  }
}
