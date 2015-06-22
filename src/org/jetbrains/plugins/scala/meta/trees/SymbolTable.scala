package org.jetbrains.plugins.scala.meta.trees

import com.intellij.psi.{PsiElement, PsiPackage}
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.{api => p, types => ptype}

import scala.meta.internal.{ast => m, semantic => h}

trait SymbolTable {
  self: Converter =>


  def isLocal(elem: PsiElement): Boolean = {
    elem match {
      case e: ScTypeDefinition => e.isLocal
      case e: ScBindingPattern if e.containingClass == null => true
      case pp: params.ScParameter => true
      case _ => false
    }
  }

  def fqnameToSymbol(fqName: String): h.Symbol = {
    fqName
      .split('.')
      .dropRight(1)
      .foldLeft(h.Symbol.Root.asInstanceOf[h.Symbol])((parent, name) => h.Symbol.Global(parent, name, h.Signature.Term))
  }

  def ownerSymbol(elem: PsiElement): h.Symbol = {
    elem match {
      case mm: ScMember =>
        if (mm.containingClass == null) h.Symbol.Empty else toSymbol(mm.containingClass)
      case bp: ScBindingPattern =>
        if (bp.containingClass == null) h.Symbol.Empty else toSymbol(bp.containingClass)
      case other => other ?!
    }
  }

  def toSymbol(elem: PsiElement): h.Symbol = {
    elem match {
      case _ if isLocal(elem) =>
        // aka LightVirtualFile in case of running in test
        if (elem.getContainingFile.getVirtualFile == null)
          h.Symbol.Local(elem.getContainingFile.getName + ":" + elem.getTextOffset)
        else
          h.Symbol.Local(elem.getContainingFile.getVirtualFile.getCanonicalPath + "\n" + elem.getTextOffset)
      case td: ScTypeDefinition if !td.qualifiedName.contains(".") => // empty package defn
        h.Symbol.Global(h.Symbol.Empty, td.name, h.Signature.Type)
      case td: ScTemplateDefinition =>
        h.Symbol.Global(fqnameToSymbol(td.qualifiedName), td.name, h.Signature.Type)
      case td: ScTypeDefinition =>
        h.Symbol.Global(toSymbol(td.parent.get), td.name, h.Signature.Type)
      case td: ScFieldId =>
        val owner = td.nameContext match {
          case vd: ScValueDeclaration => ownerSymbol(vd)
          case vd: ScVariableDeclaration => ownerSymbol(vd)
          case other => other ?!
        }
        h.Symbol.Global(owner, td.name, h.Signature.Term)
      case td: ScFunction =>
        // meta trees don't resolve unapply methods
        if (td.name == "unapply")
          toSymbol(td.containingClass)
        else {
          val jvmsig = DebuggerUtil.getFunctionJVMSignature(td).getName(null)
          h.Symbol.Global(ownerSymbol(td), td.name, h.Signature.Method(jvmsig))
        }
      case pc: ScPackaging =>
        toSymbol(pc.reference.get.bind().get.element)
      case pp: PsiPackage if pp.getName == null =>
        h.Symbol.Root
      case pc: PsiPackage =>
        h.Symbol.Global(toSymbol(pc.getParentPackage), pc.getName, h.Signature.Term)
      case cr: ScStableCodeReferenceElement =>
        toSymbol(cr.resolve())
      case ta: ScTypeAlias =>
        h.Symbol.Global(ownerSymbol(ta), ta.name, h.Signature.Type)
      case bp: ScBindingPattern =>
        h.Symbol.Global(ownerSymbol(bp), bp.name, h.Signature.Term)
      case _ => elem ?!
    }
  }
}
