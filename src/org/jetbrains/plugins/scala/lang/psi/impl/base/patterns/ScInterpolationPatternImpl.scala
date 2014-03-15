package org.jetbrains.plugins.scala
package lang.psi.impl.base.patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedPrefixReference
import org.jetbrains.plugins.scala.lang.psi.impl.base.{ScLiteralImpl, ScInterpolationStableCodeReferenceElementImpl}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor

/**
 * @author kfeodorov
 * @since 01.03.14.
 */
class ScInterpolationPatternImpl (_node: ASTNode) extends ScLiteralImpl(_node) with ScInterpolationPattern {

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
  override def isMultiLineString: Boolean = getText.endsWith("\"\"\"")
}