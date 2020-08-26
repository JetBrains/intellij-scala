package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, ifReadAllowed}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
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

  override def assignment: Option[PsiElement] = Option(findChildByType[PsiElement](ScalaTokenTypes.tASSIGN))

  override def expr: Option[ScExpression] = byPsiOrStub(findChild(classOf[ScExpression]))(_.bodyExpression)

  override def bindings: collection.Seq[ScBindingPattern] = pList match {
    case null => Seq.empty
    case ScPatternList(collection.Seq(pattern)) => pattern.bindings
    case ScPatternList(patterns) => patterns.flatMap(_.bindings)
  }

  override def `type`(): TypeResult = typeElement match {
    case Some(te) => te.`type`()
    case None => expr.map(_.`type`().map(ScLiteralType.widenRecursive)).
      getOrElse(Failure(ScalaBundle.message("cannot.infer.type.without.an.expression")))
  }

  override def typeElement: Option[ScTypeElement] = byPsiOrStub(findChild(classOf[ScTypeElement]))(_.typeElement)

  override def annotationAscription: Option[ScAnnotations] =
    assignment.flatMap(_.getPrevSiblingNotWhitespaceComment match {
      case prev: ScAnnotations => Some(prev)
      case _                   => None
    })

  override def pList: ScPatternList = getStubOrPsiChild(ScalaElementType.PATTERN_LIST)

  override def toString: String = "ScVariableDefinition: " + ifReadAllowed(declaredNames.mkString(", "))("")
}