package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScSuperReference
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{ScUAnnotated, ScUExpression}
import org.jetbrains.plugins.scala.lang.psi.uast.converter.BaseScala2UastConverter
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.uast.{UIdentifier, USuperExpression, USuperExpressionAdapter}

/**
  * [[ScSuperReference]] adapter for the [[USuperExpression]]
  *
  * @param scExpression Scala PSI element representing `super` reference
  */
class ScUSuperExpression(override protected val scExpression: ScSuperReference,
                         override protected val parent: LazyUElement)
    extends USuperExpressionAdapter
    with ScUExpression
    with ScUAnnotated {

  @Nullable
  override def getLabel: String =
    Option(scExpression.findFirstChildByType(ScalaTokenTypes.kSUPER))
      .map(_.getText)
      .orNull

  @Nullable
  override def getLabelIdentifier: UIdentifier =
    Option(scExpression.findFirstChildByType(ScalaTokenTypes.kSUPER))
      .map(BaseScala2UastConverter.createUIdentifier(_, this))
      .orNull

  @Nullable
  override def resolve(): PsiElement = scExpression.drvTemplate.orNull
}
