package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import psi.stubs.{ScVariableStub}
import api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import psi.types.result.TypingContext
import api.ScalaElementVisitor
import com.intellij.psi.PsiElementVisitor

/**
 * @author Alexander Podkhalyuzin
 */

class ScVariableDeclarationImpl extends ScalaStubBasedElementImpl[ScVariable] with ScVariableDeclaration {
  def this(node: ASTNode) = {this (); setNode(node)}

  def this(stub: ScVariableStub) = {this (); setStub(stub); setNode(null)}

  override def toString: String = "ScVariableDeclaration: " + declaredElements.map(_.name).mkString(", ")

  def getType(ctx: TypingContext) = wrap(typeElement) flatMap {_.getType(TypingContext.empty)}

  def declaredElements = getIdList.fieldIds

  def typeElement: Option[ScTypeElement] = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScVariableStub].getTypeElement
    }
    else findChild(classOf[ScTypeElement])
  }

  def getIdList: ScIdList = findChildByClass(classOf[ScIdList])

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitVariableDeclaration(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitVariableDeclaration(this)
      case _ => super.accept(visitor)
    }
  }
}