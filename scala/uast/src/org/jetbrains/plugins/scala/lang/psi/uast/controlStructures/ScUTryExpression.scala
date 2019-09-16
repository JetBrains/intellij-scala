package org.jetbrains.plugins.scala.lang.psi.uast.controlStructures

import _root_.java.util

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScCatchBlock, ScTry}
import org.jetbrains.plugins.scala.lang.psi.uast.converter.BaseScala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{
  ScUAnnotated,
  ScUElement,
  ScUExpression
}
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.plugins.scala.lang.psi.uast.utils.JavaCollectionsCommon
import org.jetbrains.uast._

import scala.collection.JavaConverters._

/**
  * [[ScTry]] adapter for the [[UTryExpression]]
  *
  * @param scExpression Scala PSI element representing `try {} catch {} [finally {}]` block
  */
class ScUTryExpression(override protected val scExpression: ScTry,
                       override protected val parent: LazyUElement)
    extends UTryExpressionAdapter
    with ScUExpression
    with ScUAnnotated {

  override def getCatchClauses: util.List[UCatchClause] =
    scExpression.catchBlock match {
      case Some(ScCatchBlock(clausesBlock)) =>
        clausesBlock.caseClauses
          .flatMap(_.convertTo[UCatchClause](this))
          .toList
          .asJava
      case None => JavaCollectionsCommon.newEmptyJavaList
    }

  @Nullable
  override def getFinallyClause: UExpression =
    scExpression.finallyBlock
      .map(_.expression.convertToUExpressionOrEmpty(this))
      .orNull

  @Nullable
  override def getFinallyIdentifier: UIdentifier =
    Option(scExpression.findFirstChildByType(ScalaTokenTypes.kFINALLY))
      .map(createUIdentifier(_, this))
      .orNull

  override def getHasResources: Boolean = false

  override def getResourceVariables: util.List[UVariable] =
    JavaCollectionsCommon.newEmptyJavaList

  override def getTryClause: UExpression =
    scExpression.expression.convertToUExpressionOrEmpty(this)

  override def getTryIdentifier: UIdentifier =
    createUIdentifier(
      scExpression.findFirstChildByType(ScalaTokenTypes.kTRY),
      this
    )
}

/**
  * [[ScCaseClause]] adapter for the [[UCatchClause]]
  *
  * @param scElement Scala PSI element representing case clause
  *                  inside catch block
  */
class ScUCatchExpression(override protected val scElement: ScCaseClause,
                         override protected val parent: LazyUElement)
    extends UCatchExpressionAdapter
    with ScUElement {

  override type PsiFacade = PsiElement

  override def getBody: UExpression =
    scElement.expr.convertToUExpressionOrEmpty(this)

  override def getParameters: util.List[UParameter] =
    JavaCollectionsCommon.newEmptyJavaList

  override def getTypeReferences: util.List[UTypeReferenceExpression] =
    JavaCollectionsCommon.newEmptyJavaList
}
