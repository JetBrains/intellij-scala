package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiModifierListOwnerExt, ifReadAllowed}
import org.jetbrains.plugins.scala.lang.lexer.{ScalaModifier, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScBegin
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPropertyStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScPropertyElementType
import org.jetbrains.plugins.scala.lang.psi.types.ScLiteralType
import org.jetbrains.plugins.scala.lang.psi.types.result._

final class ScPatternDefinitionImpl private[psi](stub: ScPropertyStub[ScPatternDefinition],
                                                 nodeType: ScPropertyElementType[ScPatternDefinition],
                                                 node: ASTNode)
  extends ScValueOrVariableImpl(stub, nodeType, node) with ScPatternDefinition with ScBegin {

  override def toString: String = ifReadAllowed {
    val names = declaredNames
    if (names.isEmpty) "ScPatternDefinition"
    else "ScPatternDefinition: " + declaredNames.mkString(", ")
  }("")

  override def isStable: Boolean =
    if (this.hasModifierPropertyScala(ScalaModifier.LAZY))
      this.hasFinalModifier || this.isTopLevel || // top level `lazy val x = 1` is effectively final
        !this.isInScala3File || // in scala2 lazy val can be referenced with `.type` even without explicit type
        typeElement.exists(_.singleton)
    else
      true

  override def bindings: Seq[ScBindingPattern] = Option(pList).map(_.bindings).getOrElse(Seq.empty)

  override def declaredElements: Seq[ScBindingPattern] = bindings

  override def `type`(): TypeResult = typeElement match {
    case Some(te) => te.`type`()
    case _ =>
      expr.toRight {
        new Failure(ScalaBundle.nls("cannot.infer.type.without.an.expression"))
      }.flatMap {
        _.`type`()
      }.map {
        case literalType: ScLiteralType if this.hasFinalModifier => literalType
        case t => ScLiteralType.widenRecursive(t)
      }
  }

  override def expr: Option[ScExpression] = byPsiOrStub(findChild[ScExpression])(_.bodyExpression)

  override def typeElement: Option[ScTypeElement] = byPsiOrStub(findChild[ScTypeElement])(_.typeElement)

  override def annotationAscription: Option[ScAnnotations] =
    assignment.flatMap(_.getPrevSiblingNotWhitespaceComment match {
      case prev: ScAnnotations => Some(prev)
      case _                   => None
    })

  override def pList: ScPatternList = getStubOrPsiChild(ScalaElementType.PATTERN_LIST)

  override protected def keywordTokenType: IElementType = ScalaTokenTypes.kVAL

  override def namedTag: Option[ScNamedElement] = declaredElements.headOption
}