package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiClass, PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.stubs.ScParameterStub

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScClassParameterImpl private (stub: ScParameterStub, node: ASTNode)
  extends ScParameterImpl(stub, ScalaElementTypes.CLASS_PARAM, node) with ScClassParameter {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScParameterStub) = this(stub, null)

  override def toString: String = "ClassParameter: " + name

  override def isVal: Boolean = byStubOrPsi(_.isVal)(findChildByType(ScalaTokenTypes.kVAL) != null)

  override def isVar: Boolean = byStubOrPsi(_.isVar)(findChildByType(ScalaTokenTypes.kVAR) != null)

  def isPrivateThis: Boolean = {
    if (!isEffectiveVal) return true
    getModifierList.accessModifier match {
      case Some(am) =>
        am.isThis && am.isPrivate
      case _ => false
    }
  }

  override def isStable: Boolean = byStubOrPsi(_.isStable)(findChildByType(ScalaTokenTypes.kVAR) == null)

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