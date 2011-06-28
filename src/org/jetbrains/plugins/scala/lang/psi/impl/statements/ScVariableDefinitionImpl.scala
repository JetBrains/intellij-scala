package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import parser.ScalaElementTypes
import stubs.ScVariableStub
import psi.types.result.TypingContext
import com.intellij.psi.PsiElementVisitor
import api.ScalaElementVisitor
import api.expr.ScExpression

/**
 * @author Alexander Podkhalyuzin
 */

class ScVariableDefinitionImpl extends ScalaStubBasedElementImpl[ScVariable] with ScVariableDefinition {
  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  def expr: ScExpression = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScVariableStub].getBodyExpr match {
        case Some(expr) => return expr
        case _ =>
      }
    }
    findChildByClassScala(classOf[ScExpression])
  }

  def this(node: ASTNode) = {this (); setNode(node)}

  def this(stub: ScVariableStub) = {this (); setStub(stub); setNode(null)}

  override def toString: String = "ScVariableDefinition"

  def bindings: Seq[ScBindingPattern] = {
    val plist = this.pList
    if (plist != null) plist.patterns.flatMap((p: ScPattern) => p.bindings) else Seq.empty
  }

  def getType(ctx: TypingContext) = typeElement match {
    case Some(te) => te.getType(ctx)
    case None => expr.getType(TypingContext.empty)
  }

  def typeElement: Option[ScTypeElement] = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScVariableStub].getTypeElement
    }
    else findChild(classOf[ScTypeElement])
  }

  def pList: ScPatternList = {
    val stub = getStub
    if (stub != null) {
      stub.getChildrenByType(ScalaElementTypes.PATTERN_LIST, ScalaPsiUtil.arrayFactory[ScPatternList]).apply(0)
    } else findChildByClass(classOf[ScPatternList])
  }
}