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
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClauses
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ExtractClass
import org.jetbrains.plugins.scala.lang.psi.types.api.designator._
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
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
            case Typeable(PatternGenerationStrategy(strategy)) =>
              val lookupElement = createLookupElement(ItemText)(tailText = RendererTailText) {
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

  private[lang] val ItemText = MATCH
  private[lang] val RendererTailText = " (exhaustive)"

  private object PatternGenerationStrategy {

    def unapply(`type`: ScType): Option[PatternGenerationStrategy] = {
      val valueType = `type`.extractDesignatorSingleton.getOrElse(`type`)

      valueType match {
        case ScProjectionType(DesignatorOwner(enumeration@ScalaEnumeration()), _) =>
          Some(new ScalaEnumGenerationStrategy(valueType, enumeration))
        case ScDesignatorType(enum: PsiClass) if enum.isEnum =>
          Some(new JavaEnumGenerationStrategy(valueType, enum))
        case ExtractClass(SealedDefinition(classes)) =>
          val inheritors = Inheritors(classes)
          if (inheritors.namedInheritors.isEmpty && inheritors.anonymousInheritors.isEmpty) None
          else Some(new SealedClassGenerationStrategy(inheritors))
        case _ => None
      }
    }

    private[this] object ScalaEnumeration {

      private[this] val EnumerationFQN = "scala.Enumeration"

      def unapply(scalaObject: ScObject): Boolean =
        scalaObject.supers
          .map(_.qualifiedName)
          .contains(EnumerationFQN)
    }

  }

  private sealed trait PatternGenerationStrategy {

    def patterns(implicit place: PsiElement): Seq[PatternComponents]
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

      ClausesInsertHandler.replaceTextPhase {
        patterns.map(_.text).map { patternText =>
          s"$CASE $patternText ${ScalaPsiUtil.functionArrow}"
        }.mkString(s"$MATCH {\n", "\n", "\n}")
      }

      val whiteSpace = onTargetElement { statement =>
        replacePsiPhase(patterns, statement.getCaseClauses)
      }

      moveCaretPhase(whiteSpace.getStartOffset + 1)
    }

    private def replacePsiPhase(components: Seq[PatternComponents],
                                clauses: ScCaseClauses): PsiWhiteSpaceImpl = {
      val caseClauses = clauses.caseClauses

      adjustTypesPhase(
        strategy.isInstanceOf[SealedClassGenerationStrategy],
        caseClauses.flatMap(_.pattern).zip(components): _*
      )

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
