package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import java.{util => ju}

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScMatch}
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{ScUAnnotated, ScUElement, ScUExpression}
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.plugins.scala.lang.psi.uast.kinds.ScalaSpecialExpressionKinds
import org.jetbrains.uast._

import scala.jdk.CollectionConverters._

/**
  * [[ScMatch]] adapter for the [[USwitchExpression]]
  *
  * @param scExpression Scala PSI element representing `match` block
  */
final class ScUSwitchExpression(override protected val scExpression: ScMatch,
                                override protected val parent: LazyUElement)
    extends USwitchExpressionAdapter
    with ScUExpression
    with ScUAnnotated {

  override def getBody: UExpressionList =
    scExpression.caseClauses
      .convertTo[UExpressionList](this)
      .getOrElse(
        new ScUEmptyExpressionList(
          scExpression.caseClauses,
          LazyUElement.just(this)
        )
      )

  @Nullable
  override def getExpression: UExpression =
    scExpression.expression.map(_.convertToUExpressionOrEmpty(this)).orNull

  override def getSwitchIdentifier: UIdentifier =
    createUIdentifier(
      scExpression.findFirstChildByType(ScalaTokenTypes.kMATCH),
      this
    )
}

/**
  * [[ScCaseClauses]] adapter for the [[UExpressionList]]
  *
  * @param scElement Scala PSI element representing case clauses block
  */
final class ScUCaseClausesList(override protected val scElement: ScCaseClauses,
                               override protected val parent: LazyUElement)
    extends UExpressionListAdapter
    with ScUElement
    with ScUAnnotated {

  import RenderUtils._

  override type PsiFacade = PsiElement

  private def expressions: Seq[UExpression] =
    scElement.caseClauses.map(_.convertToUExpressionOrEmpty(this))

  override def getExpressions: ju.List[UExpression] = expressions.asJava

  override def getKind: UastSpecialExpressionKind =
    ScalaSpecialExpressionKinds.Match

  override def asRenderString: String =
    expressions.asBlock(margin = "    ", inBrackets = false)
}

/**
  * [[ScCaseClause]] adapter for the [[USwitchClauseExpressionWithBody]]
  *
  * @param scElement Scala PSI element representing case clause
  */
final class ScUCaseClause(override protected val scElement: ScCaseClause,
                          override protected val parent: LazyUElement)
    extends USwitchClauseExpressionWithBodyAdapter
    with ScUElement
    with ScUAnnotated {

  override type PsiFacade = PsiElement

  override def getBody: UExpressionList =
    scElement.expr
      .collect {
        case b: ScBlock => new ScUCaseClauseBodyList(b, LazyUElement.just(this))
      }
      .getOrElse(new ScUEmptyExpressionList(scElement, LazyUElement.just(this)))

  override def getCaseValues: ju.List[UExpression] =
    Seq(scElement.pattern.convertToUExpressionOrEmpty(this)).asJava
}

/**
  * [[ScBlock]] adapter for the [[UExpressionList]]
  *
  * @param scElement Scala PSI element representing body of the case clause
  */
final class ScUCaseClauseBodyList(override protected val scElement: ScBlock,
                                  override protected val parent: LazyUElement)
    extends UExpressionListAdapter
    with ScUElement
    with ScUAnnotated {

  import RenderUtils._

  override type PsiFacade = PsiElement

  private def expressions: Seq[UExpression] =
    scElement.statements.map(_.convertToUExpressionOrEmpty(this))

  override def getExpressions: ju.List[UExpression] = expressions.asJava

  override def getKind: UastSpecialExpressionKind =
    ScalaSpecialExpressionKinds.CaseClause

  override def asRenderString: String = expressions.asBlock(margin = "    ")
}

/**
  * Empty [[UExpressionList]] implementation, used when it is impossible
  * by some reason to convert element to list properly.
  *
  * @param scElement Scala PSI element representing source element which
  *                  cannot be converted properly
  */
final class ScUEmptyExpressionList(override protected val scElement: PsiElement,
                                   override protected val parent: LazyUElement)
    extends UExpressionListAdapter
    with ScUElement {

  override type PsiFacade = PsiElement

  @Nullable
  override def getJavaPsi: PsiElement = null

  override def getExpressions: ju.List[UExpression] =
    ju.Collections.emptyList()

  override def getKind: UastSpecialExpressionKind =
    ScalaSpecialExpressionKinds.EmptyList

  override def getUAnnotations: ju.List[UAnnotation] =
    ju.Collections.emptyList()
}

private object RenderUtils {
  implicit class RenderExt(private val exprs: Seq[UExpression]) extends AnyVal {
    def asBlock(margin: String = "", inBrackets: Boolean = true): String = {
      val content = exprs
        .flatMap(_.asRenderString().linesIterator)
        .map(margin + _)
        .mkString("\n")

      if (inBrackets) s"{\n$content\n}"
      else content
    }
  }
}
