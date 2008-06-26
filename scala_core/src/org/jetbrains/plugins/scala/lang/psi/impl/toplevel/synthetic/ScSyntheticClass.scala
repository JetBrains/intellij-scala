package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic

import api.toplevel.ScNamedElement
import api.statements.ScFunction
import types.ScType

import com.intellij.util.IncorrectOperationException
import com.intellij.psi._
import com.intellij.psi.impl.light.LightElement

import _root_.scala.collection.mutable.ListBuffer

abstract class SyntheticNamedElement(manager : PsiManager, name : String)
extends LightElement(manager, ScalaFileType.SCALA_LANGUAGE) with PsiNamedElement {
  def getName = name
  def setName(newName : String) = throw new IncorrectOperationException("nonphysical element")
  def copy = throw new IncorrectOperationException("nonphysical element")
  def accept(v : PsiElementVisitor) = throw new IncorrectOperationException("should not call")
}

class ScSyntheticClass(manager : PsiManager, val name : String)
  extends SyntheticNamedElement(manager, name) {

  def getText = "" //todo

  override def toString = "Synthetic class"

  val methods = new ListBuffer[ScSyntheticFunction]

  def addMethod(method : ScSyntheticFunction) = methods += method

  import com.intellij.psi.scope.PsiScopeProcessor
  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    for (method <- methods) {
      if (!processor.execute(method, state)) return false
    }
    true
  }
}

class ScSyntheticFunction(manager : PsiManager, val name : String, val ret : ScType, val params : Seq[ScType])
  extends SyntheticNamedElement(manager, name) { //todo provide function interface

  def getText = "" //todo

  override def toString = "Synthetic method"
}