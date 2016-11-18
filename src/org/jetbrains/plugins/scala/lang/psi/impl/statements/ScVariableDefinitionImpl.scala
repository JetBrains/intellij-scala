package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScVariableStub
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult, TypingContext}

/**
 * @author Alexander Podkhalyuzin
 */

class ScVariableDefinitionImpl private (stub: StubElement[ScVariable], nodeType: IElementType, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node) with ScVariableDefinition {
  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  def expr: Option[ScExpression] = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScVariableStub].bodyExpression
    }
    Option(findChildByClassScala(classOf[ScExpression]))
  }

  def this(node: ASTNode) = {this(null, null, node)}

  def this(stub: ScVariableStub) = {this(stub, ScalaElementTypes.VARIABLE_DEFINITION, null)}

  override def toString: String = "ScVariableDefinition"

  def bindings: Seq[ScBindingPattern] = {
    val plist = this.pList
    if (plist != null) plist.patterns.flatMap((p: ScPattern) => p.bindings) else Seq.empty
  }

  def getType(ctx: TypingContext): TypeResult[ScType] = typeElement match {
    case Some(te) => te.getType(ctx)
    case None => expr.map(_.getType(TypingContext.empty))
            .getOrElse(Failure("Cannot infer type without an expression", Some(this)))
  }

  def typeElement: Option[ScTypeElement] = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScVariableStub].typeElement
    }
    else findChild(classOf[ScTypeElement])
  }

  def pList: ScPatternList = {
    val stub = getStub
    if (stub != null) {
      stub.getChildrenByType(ScalaElementTypes.PATTERN_LIST, JavaArrayFactoryUtil.ScPatternListFactory).apply(0)
    } else findChildByClass(classOf[ScPatternList])
  }
}