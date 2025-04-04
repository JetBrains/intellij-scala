package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScNamedConstructorArgPattern
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

class ScNamedConstructorArgPatternImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScPatternImpl with ScNamedConstructorArgPattern {
  override def nameId: PsiElement = nameElement.orNull
  override def nameElement: Option[PsiElement] = findFirstChildByType(ScalaTokenTypes.tIDENTIFIER)

  override def `type`(): TypeResult = this.flatMap(subPattern)(_.`type`())

  override protected def isIrrefutableForImpl(t: Option[ScType]): Boolean = subPattern.forall(_.isIrrefutableFor(t))
}