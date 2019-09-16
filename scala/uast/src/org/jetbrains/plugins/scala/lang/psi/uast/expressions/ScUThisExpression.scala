package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScThisReference
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{ScUAnnotated, ScUExpression}
import org.jetbrains.plugins.scala.lang.psi.uast.converter.BaseScala2UastConverter
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.uast.{UIdentifier, UThisExpression, UThisExpressionAdapter}

/**
  * [[ScThisReference]] adapter for the [[UThisExpression]]
  *
  * @param scExpression Scala PSI element representing `this` reference
  */
class ScUThisExpression(override protected val scExpression: ScThisReference,
                        override protected val parent: LazyUElement)
    extends UThisExpressionAdapter
    with ScUExpression
    with ScUAnnotated {

  @Nullable
  override def getLabel: String =
    Option(scExpression.findFirstChildByType(ScalaTokenTypes.kTHIS))
      .map(_.getText)
      .orNull

  @Nullable
  override def getLabelIdentifier: UIdentifier =
    Option(scExpression.findFirstChildByType(ScalaTokenTypes.kTHIS))
      .map(BaseScala2UastConverter.createUIdentifier(_, this))
      .orNull

  @Nullable
  override def resolve(): PsiElement = scExpression.refTemplate.orNull
}
