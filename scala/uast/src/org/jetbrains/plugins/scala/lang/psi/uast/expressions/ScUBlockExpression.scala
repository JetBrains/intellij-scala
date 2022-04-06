package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import java.util

import com.intellij.psi.{PsiElement, PsiType}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.MethodValue
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScExpression, ScReturn, ScUnderScoreSectionUtil}
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{ScUAnnotated, ScUElement, ScUExpression}
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.plugins.scala.util.HashBuilder._
import org.jetbrains.uast.{UBlockExpression, UBlockExpressionAdapter, UElement, UExpression, ULambdaExpression, UMethod, UVariable}

import scala.jdk.CollectionConverters._

trait ScUBlockCommon
    extends UBlockExpressionAdapter
    with UExpression
    with ScUAnnotated {

  protected def statements: Seq[PsiElement]

  // KT-23557
  /** Wraps last statement in [[ScUImplicitReturnExpression]] if needed */
  override def getExpressions: util.List[UExpression] = {
    val (content, lastStatement) = statements.splitAt(statements.size - 1)

    val convertedContent = content.map(_.convertToUExpressionOrEmpty(this))
    val convertedLastStatement = lastStatement.map { it =>
      if (ScUBlockExpression.isFunctionLastStatementWithoutReturn(it))
        ScUImplicitReturnExpression
          .convertAndWrapIntoReturn(it, LazyUElement.just(this))
      else
        it.convertToUExpressionOrEmpty(this)
    }

    (convertedContent ++ convertedLastStatement).asJava
  }
}

/**
  * [[ScBlock]] adapter for the [[UBlockExpression]]
  *
  * @param scExpression Scala PSI element representing block expression
  */
final class ScUBlockExpression(override protected val scExpression: ScBlock,
                         override protected val parent: LazyUElement)
    extends ScUBlockCommon
    with ScUExpression {

  override protected def statements: Seq[PsiElement] = scExpression.statements
}

object ScUBlockExpression {
  def isFunctionLastStatementWithoutReturn(arg: PsiElement): Boolean =
    isExplicitFunctionLastStatementWithoutReturn(arg) ||
      isImplicitFunctionLastStatementWithoutReturn(arg)

  def isExplicitFunctionLastStatementWithoutReturn(arg: PsiElement): Boolean =
    insideBlockBody(arg) || insideExpressionBody(arg)

  def isImplicitFunctionLastStatementWithoutReturn(arg: PsiElement): Boolean =
    insideImplicitLambda(arg)

  private def insideBlockBody(arg: PsiElement): Boolean =
    (
      //noinspection ScalaUnusedSymbol
      for {
        expr <- Option(arg)
        if !expr.isInstanceOf[ScReturn]
        block @ (_x: ScBlock) <- Option(expr.getParent)
        lastStmt <- block.lastStatement
        if expr == lastStmt
        scFun <- Option(block.getParent)
        uFun <- scFun.convertTo[UElement](parent = null)
      } yield uFun
    ).exists(isNonUnitResultUFunction)

  private def insideExpressionBody(arg: PsiElement): Boolean =
    (
      for {
        expr <- Option(arg)
        if !expr.isInstanceOf[ScReturn] &&
          !expr.isInstanceOf[ScBlock]
        scFun <- Option(expr.getParent)
        uFun <- scFun.convertTo[UElement](parent = null, convertLambdas = false)
      } yield uFun
    ).exists(isNonUnitResultUFunction)

  private def insideImplicitLambda(arg: PsiElement): Boolean = {
    def isImplicitLambda(element: PsiElement): Boolean =
      element match {
        case MethodValue(_) => true
        case e: ScExpression
            if ScUnderScoreSectionUtil.isUnderscoreFunction(e) =>
          true
        case _ => false
      }

    (
      for {
        expr <- Option(arg)
        if isImplicitLambda(expr)
        uFun <- expr.convertTo[ULambdaExpression](parent = null)
      } yield uFun
    ).exists(isNonUnitResultUFunction)
  }

  private def isNonUnitResultUFunction(uFun: UElement): Boolean = uFun match {
    case method: UMethod if method.getReturnType != PsiType.VOID => true
    case lambda: ULambdaExpression
        if Option(lambda.getBody).exists(_.getExpressionType != PsiType.VOID) =>
      true

    case localFunDecl: ScULocalFunctionDeclarationExpression =>
      localFunDecl.getDeclarations.asScala match {
        case collection.Seq(variable: UVariable) =>
          variable.getUastInitializer match {
            case lambda: ULambdaExpression =>
              Option(lambda.getBody).exists(_.getExpressionType != PsiType.VOID)
            case _ => false
          }
        case _ => false
      }

    case _ => false
  }
}

// See KT-23557
/** Implicit block for methods and lambdas expression bodies */
final class ScUImplicitBlockExpression(private val statement: PsiElement,
                                 override protected val parent: LazyUElement,
                                 convertLambdas: Boolean)
    extends UBlockExpressionAdapter
    with ScUElement
    with ScUAnnotated {

  override type PsiFacade = PsiElement
  @Nullable
  override protected val scElement: PsiFacade = null

  override def getExpressions: util.List[UExpression] =
    Seq(
      if (ScUBlockExpression.isFunctionLastStatementWithoutReturn(statement))
        ScUImplicitReturnExpression
          .convertAndWrapIntoReturn(
            statement,
            LazyUElement.just(this),
            convertLambdas
          )
      else
        statement.convertToUExpressionOrEmpty(parent = this, convertLambdas)
    ).asJava

  override def equals(other: Any): Boolean = other match {
    case other: ScUImplicitBlockExpression if super.equals(other) =>
      statement == other.statement
    case _ => false
  }

  override def hashCode(): Int =
    super.hashCode #+ statement
}

object ScUImplicitBlockExpression {
  def convertAndWrapIntoBlock(
    psiElement: PsiElement,
    parent: LazyUElement,
    convertLambdas: Boolean = true
  ): UBlockExpression =
    new ScUImplicitBlockExpression(psiElement, parent, convertLambdas)
}
