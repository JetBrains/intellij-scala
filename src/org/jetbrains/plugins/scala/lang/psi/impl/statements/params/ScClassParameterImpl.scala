package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.lang.ASTNode
import stubs.elements.wrappers.DummyASTNode
import stubs.ScParameterStub

import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import com.intellij.psi.{PsiClass, PsiElement}
import api.toplevel.typedef.ScClass

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScClassParameterImpl(node: ASTNode) extends ScParameterImpl(node) with ScClassParameter {

  def this(stub: ScParameterStub) = {
    this(DummyASTNode)
    setStub(stub)
    setNode(null)
  }

  override def toString: String = "ClassParameter"

  def isVal: Boolean = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScParameterStub].isVal
    }
    findChildByType(ScalaTokenTypes.kVAL) != null
  }
  def isVar: Boolean = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScParameterStub].isVar
    }
    findChildByType(ScalaTokenTypes.kVAR) != null
  }

  override def isStable: Boolean = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScParameterStub].isStable
    }
    !isVar
  }

  override def getOriginalElement: PsiElement = {
    val containingClass = getContainingClass
    if (containingClass == null) return this
    val originalClass: PsiClass = containingClass.getOriginalElement.asInstanceOf[PsiClass]
    if (containingClass eq originalClass) return this
    if (!originalClass.isInstanceOf[ScClass]) return this
    val c = originalClass.asInstanceOf[ScClass]
    val iterator = c.parameters.iterator
    while (iterator.hasNext) {
      val param = iterator.next()
      if (param.name == name) return param
    }
    this
  }
}