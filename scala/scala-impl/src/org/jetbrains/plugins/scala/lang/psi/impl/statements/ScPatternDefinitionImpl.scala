package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScValueStub
import org.jetbrains.plugins.scala.lang.psi.types.ScLiteralType
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
* @author Alexander Podkhalyuzin
*/

class ScPatternDefinitionImpl private (stub: ScValueStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementTypes.PATTERN_DEFINITION, node) with ScPatternDefinition {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScValueStub) = this(stub, null)

  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = ifReadAllowed {
    val names = declaredNames
    if (names.isEmpty) "ScPatternDefinition"
    else "ScPatternDefinition: " + declaredNames.mkString(", ")
  }("")

  def bindings: Seq[ScBindingPattern] = pList match {
    case null => Seq.empty
    case ScPatternList(Seq(pattern)) => pattern.bindings
    case ScPatternList(patterns) => patterns.flatMap(_.bindings)
  }

  def declaredElements: Seq[ScBindingPattern] = bindings

  def `type`(): TypeResult = {
    typeElement match {
      case Some(te) => te.`type`()
      //TODO the condition looks extremely hacky
      case None if expr.nonEmpty => expr.get.`type`().map(t => if (hasFinalModifier && t.isInstanceOf[ScLiteralType]) t else ScLiteralType.widenRecursive(t))
      case _ => Failure("Cannot infer type without an expression")
    }
  }

  def expr: Option[ScExpression] = byPsiOrStub(findChild(classOf[ScExpression]))(_.bodyExpression)

  def typeElement: Option[ScTypeElement] = byPsiOrStub(findChild(classOf[ScTypeElement]))(_.typeElement)

  def pList: ScPatternList = getStubOrPsiChild(ScalaElementTypes.PATTERN_LIST)
}