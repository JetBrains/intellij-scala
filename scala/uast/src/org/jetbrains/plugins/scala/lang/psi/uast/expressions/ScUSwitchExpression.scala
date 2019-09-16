package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import _root_.java.util

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{
  ScCaseClause,
  ScCaseClauses
}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScMatch}
import org.jetbrains.plugins.scala.lang.psi.uast.converter.BaseScala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{
  ScUAnnotated,
  ScUElement,
  ScUExpression
}
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.plugins.scala.lang.psi.uast.kinds.ScalaSpecialExpressionKinds
import org.jetbrains.plugins.scala.lang.psi.uast.utils.JavaCollectionsCommon
import org.jetbrains.uast._

import scala.collection.JavaConverters._

/**
  * [[ScMatch]] adapter for the [[USwitchExpression]]
  *
  * @param scExpression Scala PSI element representing `match` block
  */
class ScUSwitchExpression(override protected val scExpression: ScMatch,
                          override protected val parent: LazyUElement)
    extends USwitchExpressionAdapter
    with ScUExpression
    with ScUAnnotated {

  override def getBody: UExpressionList =
    scExpression.caseClauses
      .convertTo[UExpressionList](this)
      .getOrElse(
        new ScUEmptyExpressionList(scExpression.caseClauses, LazyUElement.just(this))
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
class ScUCaseClausesList(override protected val scElement: ScCaseClauses,
                         override protected val parent: LazyUElement)
    extends UExpressionListAdapter
    with ScUElement
    with ScUAnnotated {

  import StringUtils._

  override type PsiFacade = PsiElement

  def expressions: Seq[UExpression] =
    scElement.caseClauses.map(_.convertToUExpressionOrEmpty(this))

  override def getExpressions: util.List[UExpression] = expressions.asJava

  override def getKind: UastSpecialExpressionKind =
    ScalaSpecialExpressionKinds.Match

  override def asRenderString: String =
    expressions.map(_.asRenderString.withMargin("    ")).mkString("\n")
}

/**
  * [[ScCaseClause]] adapter for the [[USwitchClauseExpressionWithBody]]
  *
  * @param scElement Scala PSI element representing case clause
  */
class ScUCaseClause(override protected val scElement: ScCaseClause,
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

  override def getCaseValues: util.List[UExpression] =
    Seq(scElement.pattern.convertToUExpressionOrEmpty(this)).asJava
}

/**
  * [[ScBlock]] adapter for the [[UExpressionList]]
  *
  * @param scElement Scala PSI element representing body of the case clause
  */
class ScUCaseClauseBodyList(
  override protected val scElement: ScBlock,
  override protected val parent: LazyUElement
) extends UExpressionListAdapter
    with ScUElement
    with ScUAnnotated {

  import StringUtils._

  override type PsiFacade = PsiElement

  def expressions: Seq[UExpression] =
    scElement.statements.map(_.convertToUExpressionOrEmpty(this))

  override def getExpressions: util.List[UExpression] = expressions.asJava

  override def getKind: UastSpecialExpressionKind =
    ScalaSpecialExpressionKinds.CaseClause

  override def asRenderString: String =
    s"""{
       |${expressions.map(_.asRenderString.withMargin("    ")).mkString("\n")}
       |}""".stripMargin
}

/**
  * Empty [[UExpressionList]] implementation, used when it is impossible
  * by some reason to convert element to list properly.
  *
  * @param scElement Scala PSI element representing source element which
  *                  cannot be converted properly
  */
class ScUEmptyExpressionList(
  override protected val scElement: PsiElement,
  override protected val parent: LazyUElement
) extends UExpressionListAdapter
    with ScUElement {

  override type PsiFacade = PsiElement

  @Nullable
  override def getJavaPsi: PsiElement = null

  override def getExpressions: util.List[UExpression] =
    JavaCollectionsCommon.newEmptyJavaList

  override def getKind: UastSpecialExpressionKind =
    ScalaSpecialExpressionKinds.EmptyList

  override def getUAnnotations: util.List[UAnnotation] =
    JavaCollectionsCommon.newEmptyJavaList
}

private object StringUtils {
  implicit class StringOps(s: String) {
    def withMargin(margin: String): String =
      s.lines.map(margin + _).mkString("\n")
  }
}
