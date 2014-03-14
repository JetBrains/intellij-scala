package org.jetbrains.plugins.scala
package lang.psi.impl.base.patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScLiteral, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedPrefixReference
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScInterpolationStableCodeReferenceElementImpl
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import com.intellij.psi.impl.source.tree.injected.StringLiteralEscaper
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor

/**
 * @author kfeodorov
 * @since 01.03.14.
 */
class ScInterpolationPatternImpl (_node: ASTNode) extends ScalaPsiElementImpl(_node) with ScInterpolationPattern {

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "InterpolationPattern"
  override def subpatterns: Seq[ScPattern] = Option(findChildByClassScala[ScPatternArgumentList](classOf[ScPatternArgumentList])).map(_.patterns).getOrElse(Seq.empty)
  override val node: ASTNode = _node

  override def ref: ScStableCodeReferenceElement = {
    val prefix = findChildByClass(classOf[ScInterpolatedPrefixReference])
    if (null == prefix) return null

    prefix.multiResolve(incomplete = false).headOption match {
      case Some(applyFuncResolve) => applyFuncResolve.getElement.getParent.getChildren.filter{_.isInstanceOf[ScFunctionDefinition]}.lastOption match {
          case Some(unapplyFunc) => new ScInterpolationStableCodeReferenceElementImpl(unapplyFunc.getNode)
          case None => null
        }
      case None => null
    }
  }

  override def getValue: AnyRef = args.getText.drop(1) //drop leading quote

  override def getAnnotationOwner(annotationOwnerLookUp: (ScLiteral) => Option[PsiAnnotationOwner with PsiElement]): Option[PsiAnnotationOwner] = None

  override def isMultiLineString: Boolean = args.getText.endsWith("\"\"\"")

  override def isString: Boolean = true

  /**
   * This method works only for null literal (to avoid possibly dangerous usage)
   * @param tp type, which should be returned by method getTypeWithouImplicits
   */
  override def setTypeWithoutImplicits(tp: Option[ScType]): Unit = {}

  override def createLiteralTextEscaper(): LiteralTextEscaper[_ <: PsiLanguageInjectionHost] = new StringLiteralEscaper(this)

  override def updateText(text: String): PsiLanguageInjectionHost = this

  override def isValidHost: Boolean = getValue.isInstanceOf[String]
}