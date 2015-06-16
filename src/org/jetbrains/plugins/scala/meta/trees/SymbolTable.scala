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

  def fqnameToSymbol(fqName: String): h.Symbol = {
    fqName.split('.').foldLeft(h.Symbol.Root.asInstanceOf[h.Symbol])((sym, name) => h.Symbol.Global(sym, name, h.Signature.Type))
  }

  def toSymbol(elem: PsiElement): h.Symbol = {
    elem match {
        // a wild assumption that nobody sane enough would use a colon in a filename
      case _ if isLocal(elem) => h.Symbol.Local(elem.getContainingFile.getVirtualFile.getCanonicalPath + ":" + elem.getTextOffset)
      case td: p.toplevel.typedef.ScTypeDefinition if !td.qualifiedName.contains(".") => // empty package defn
        h.Symbol.Global(h.Symbol.Empty, td.name, h.Signature.Type)
      case td: p.toplevel.typedef.ScTemplateDefinition => fqnameToSymbol(td.qualifiedName)
      case td: p.toplevel.typedef.ScTypeDefinition => h.Symbol.Global(toSymbol(td.parent.get), td.name, h.Signature.Type)
      case td: p.statements.ScFunction =>
        if (td.name == "unapply")
          toSymbol(td.containingClass)
        else {
          val jvmsig = DebuggerUtil.getFunctionJVMSignature(td).getName(null)
          h.Symbol.Global(toSymbol(td.containingClass), td.name, h.Signature.Method(jvmsig))
        }
      case pc: p.toplevel.packaging.ScPackaging => toSymbol(pc.reference.get.bind().get.element)
      case pp: PsiPackage if pp.getName == null => h.Symbol.Root
      case pc: PsiPackage => h.Symbol.Global(toSymbol(pc.getParentPackage), pc.getName, h.Signature.Type)
      case cr: p.base.ScStableCodeReferenceElement => toSymbol(cr.resolve())
      case _ => elem ?!
    }
  }
}
