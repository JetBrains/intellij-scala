package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPropertyStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScPropertyElementType
import org.jetbrains.plugins.scala.lang.psi.types.ScLiteralType
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
  * @author Alexander Podkhalyuzin
  */
final class ScVariableDefinitionImpl private[psi](stub: ScPropertyStub[ScVariableDefinition],
                                                  nodeType: ScPropertyElementType[ScVariableDefinition],
                                                  node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node) with ScVariableDefinition {

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  def expr: Option[ScExpression] = byPsiOrStub(findChild(classOf[ScExpression]))(_.bodyExpression)

  override def toString: String = "ScVariableDefinition: " + ifReadAllowed(declaredNames.mkString(", "))("")

  def bindings: Seq[ScBindingPattern] = pList match {
    case null => Seq.empty
    case ScPatternList(Seq(pattern)) => pattern.bindings
    case ScPatternList(patterns) => patterns.flatMap(_.bindings)
  }

  def `type`(): TypeResult = typeElement match {
    case Some(te) => te.`type`()
    case None => expr.map(_.`type`().map(ScLiteralType.widenRecursive)).
      getOrElse(Failure("Cannot infer type without an expression"))
  }

  def typeElement: Option[ScTypeElement] = byPsiOrStub(findChild(classOf[ScTypeElement]))(_.typeElement)

  def pList: ScPatternList = getStubOrPsiChild(ScalaElementType.PATTERN_LIST)
}