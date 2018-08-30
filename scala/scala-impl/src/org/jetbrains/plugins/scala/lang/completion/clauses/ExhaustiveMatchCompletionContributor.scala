package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.codeInsight.completion._
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses, ScTypedPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ExtractClass
import org.jetbrains.plugins.scala.lang.psi.types.api.designator._
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils

class ExhaustiveMatchCompletionContributor extends ScalaCompletionContributor {

  import ExhaustiveMatchCompletionContributor._

  extend(
    CompletionType.BASIC,
    PlatformPatterns.psiElement.inside(classOf[ScSugarCallExpr]),
    new ClausesCompletionProvider(classOf[ScSugarCallExpr]) {

      import ClausesCompletionProvider._

      override protected def addCompletions(sugarCall: ScSugarCallExpr,
                                            position: PsiElement,
                                            result: CompletionResultSet): Unit = sugarCall match {
        case _: ScPrefixExpr =>
        case ScSugarCallExpr(operand, operation, _) if operation.isAncestorOf(position) =>
          operand match {
            case PatternGenerationStrategy(strategy) =>
              val lookupElement = createLookupElement(itemText)(tailText = rendererTailText) {
                createInsertHandler(strategy)(sugarCall)
              }
              result.addElement(lookupElement)
            case _ =>
          }
        case _ =>
      }
    }
  )
}

object ExhaustiveMatchCompletionContributor {

  import ScalaKeyword._

  private[lang] val Match = MATCH

  private[lang] def itemText = Match

  private[lang] val Exhaustive = "exhaustive"

  private[lang] def rendererTailText = s" ($Exhaustive)"

  private[lang] sealed trait PatternGenerationStrategy {

    def patterns(implicit place: PsiElement): Seq[PatternComponents]
  }

  private[lang] object PatternGenerationStrategy {

    def unapply(expression: ScExpression): Option[PatternGenerationStrategy] =
      expression.`type`().toOption.map { `type` =>
        `type`.extractDesignatorSingleton.getOrElse(`type`)
      }.collect {
        case valueType@ScProjectionType(DesignatorOwner(enumeration@ScalaEnumeration()), _) =>
          new ScalaEnumGenerationStrategy(valueType, enumeration)
        case valueType@ScDesignatorType(enum: PsiClass) if enum.isEnum =>
          new JavaEnumGenerationStrategy(valueType, enum)
        case ExtractClass(SealedDefinition(inheritors)) if inheritors.namedInheritors.nonEmpty =>
          new SealedClassGenerationStrategy(inheritors)
      }

    private[this] object ScalaEnumeration {

      private[this] val EnumerationFQN = "scala.Enumeration"

      def unapply(scalaObject: ScObject): Boolean =
        scalaObject.supers
          .map(_.qualifiedName)
          .contains(EnumerationFQN)
    }

  }

  private[lang] def statementText(patterns: Seq[PatternComponents])
                                 (implicit place: PsiElement): String =
    patterns.map(_.text).map { patternText =>
      s"$CASE $patternText ${ScalaPsiUtil.functionArrow}"
    }.mkString(s"$MATCH {\n", "\n", "\n}")

  private[lang] def adjustTypesPhase(strategy: PatternGenerationStrategy,
                                     components: Seq[PatternComponents],
                                     caseClauses: Seq[ScCaseClause]): Unit =
    ClausesInsertHandler.adjustTypesPhase(
      strategy.isInstanceOf[SealedClassGenerationStrategy],
      caseClauses.flatMap(_.pattern).zip(components): _*
    ) {
      case ScTypedPattern(typeElement) => typeElement
    }

  private class SealedClassGenerationStrategy(inheritors: Inheritors) extends PatternGenerationStrategy {

    override def patterns(implicit place: PsiElement): Seq[PatternComponents] =
      inheritors.exhaustivePatterns
  }

  private class JavaEnumGenerationStrategy(valueType: ScType, enum: PsiClass) extends PatternGenerationStrategy {

    override def patterns(implicit place: PsiElement): Seq[TypedPatternComponents] = {
      val qualifiedName = valueType.presentableText

      enum.getFields.collect {
        case constant: PsiEnumConstant => new StablePatternComponents(enum, qualifiedName, constant.getName)
      }
    }
  }

  private class ScalaEnumGenerationStrategy(valueType: ScType,
                                            enumeration: ScObject) extends PatternGenerationStrategy {

    override def patterns(implicit place: PsiElement): Seq[TypedPatternComponents] = {
      val qualifiedName = enumeration.qualifiedName

      enumeration.members.flatMap {
        case value: ScValue
          if ResolveUtils.isAccessible(value, place, forCompletion = true) &&
            isEnumerationValue(value) => value.declaredNames
        case _ => Seq.empty
      }.map {
        new StablePatternComponents(enumeration, qualifiedName, _)
      }
    }

    private def isEnumerationValue(value: ScValue) =
      value.`type`().exists(_.conforms(valueType))
  }

  private def createInsertHandler(strategy: PatternGenerationStrategy)
                                 (implicit sugarCall: ScSugarCallExpr) = new ClausesInsertHandler(classOf[ScMatchStmt]) {

    override protected def handleInsert(implicit insertionContext: InsertionContext): Unit = {
      val patterns = strategy.patterns
      ClausesInsertHandler.replaceTextPhase(statementText(patterns))

      val whiteSpace = onTargetElement { statement =>
        replacePsiPhase(patterns, statement.getCaseClauses)
      }

      moveCaretPhase(whiteSpace.getStartOffset + 1)
    }

    private def replacePsiPhase(components: Seq[PatternComponents],
                                clauses: ScCaseClauses): PsiWhiteSpaceImpl = {
      val caseClauses = clauses.caseClauses
      adjustTypesPhase(strategy, components, caseClauses)

      caseClauses.head.nextSibling.getOrElse(clauses.getNextSibling) match {
        case whiteSpace: PsiWhiteSpaceImpl =>
          whiteSpace.replaceWithText(" " + whiteSpace.getText).asInstanceOf[PsiWhiteSpaceImpl]
      }
    }

    private def moveCaretPhase(offset: Int)
                              (implicit insertionContext: InsertionContext): Unit = {
      val InsertionContextExt(editor, document, _, project) = insertionContext

      PsiDocumentManager.getInstance(project)
        .doPostponedOperationsAndUnblockDocument(document)
      editor.getCaretModel.moveToOffset(offset)
    }
  }
}
