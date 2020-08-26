package org.jetbrains.plugins.scala.lang.psi.uast.controlStructures

import java.{util => ju}

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScCatchBlock, ScTry}
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{ScUAnnotated, ScUElement, ScUExpression}
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.uast._

import scala.jdk.CollectionConverters._

/**
  * [[ScTry]] adapter for the [[UTryExpression]]
  *
  * @param scExpression Scala PSI element representing `try {} catch {} [finally {}]` block
  */
final class ScUTryExpression(override protected val scExpression: ScTry,
                             override protected val parent: LazyUElement)
    extends UTryExpressionAdapter
    with ScUExpression
    with ScUAnnotated {

  override def getCatchClauses: ju.List[UCatchClause] =
    scExpression.catchBlock match {
      case Some(ScCatchBlock(clausesBlock)) =>
        clausesBlock.caseClauses
          .flatMap(_.convertTo[UCatchClause](parent = this))
          .asJava
      case _ => ju.Collections.emptyList()
    }

  @Nullable
  override def getFinallyClause: UExpression =
    scExpression.finallyBlock
      .map(_.expression.convertToUExpressionOrEmpty(parent = this))
      .orNull

  @Nullable
  override def getFinallyIdentifier: UIdentifier =
    Option(scExpression.findFirstChildByType(ScalaTokenTypes.kFINALLY))
      .map(createUIdentifier(_, parent = this))
      .orNull

  override def getHasResources: Boolean = false

  override def getResourceVariables: ju.List[UVariable] =
    ju.Collections.emptyList()

  override def getTryClause: UExpression =
    scExpression.expression.convertToUExpressionOrEmpty(parent = this)

  override def getTryIdentifier: UIdentifier =
    createUIdentifier(
      scExpression.findFirstChildByType(ScalaTokenTypes.kTRY),
      parent = this
    )
}

/**
  * [[ScCaseClause]] adapter for the [[UCatchClause]]
  *
  * @param scElement Scala PSI element representing case clause
  *                  inside catch block
  */
final class ScUCatchExpression(override protected val scElement: ScCaseClause,
                               override protected val parent: LazyUElement)
    extends UCatchExpressionAdapter
    with ScUElement {

  override type PsiFacade = PsiElement

  override def getBody: UExpression =
    scElement.expr.convertToUExpressionOrEmpty(parent = this)

  override def getParameters: ju.List[UParameter] =
    ju.Collections.emptyList()

  override def getTypeReferences: ju.List[UTypeReferenceExpression] =
    ju.Collections.emptyList()
}
