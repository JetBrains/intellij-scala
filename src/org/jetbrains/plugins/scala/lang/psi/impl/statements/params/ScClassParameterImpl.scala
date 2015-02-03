package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiClass, PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.stubs.ScParameterStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.wrappers.DummyASTNode

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

  override def toString: String = "ClassParameter: " + name

  override def isVal: Boolean = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScParameterStub].isVal
    }
    findChildByType[PsiElement](ScalaTokenTypes.kVAL) != null
  }
  override def isVar: Boolean = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScParameterStub].isVar
    }
    findChildByType[PsiElement](ScalaTokenTypes.kVAR) != null
  }

  def isPrivateThis: Boolean = {
    if (!isEffectiveVal) return true
    getModifierList.accessModifier match {
      case Some(am) =>
        am.isThis && am.isPrivate
      case _ => false
    }
  }

  override def isStable: Boolean = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScParameterStub].isStable
    }
    !isVar
  }

  override def getOriginalElement: PsiElement = {
    val ccontainingClass = containingClass
    if (ccontainingClass == null) return this
    val originalClass: PsiClass = ccontainingClass.getOriginalElement.asInstanceOf[PsiClass]
    if (ccontainingClass eq originalClass) return this
    if (!originalClass.isInstanceOf[ScClass]) return this
    val c = originalClass.asInstanceOf[ScClass]
    val iterator = c.parameters.iterator
    while (iterator.hasNext) {
      val param = iterator.next()
      if (param.name == name) return param
    }
    this
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitClassParameter(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitClassParameter(this)
      case _ => super.accept(visitor)
    }
  }
}