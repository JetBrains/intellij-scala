package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup._
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMatchStmt, ScPostfixExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ExtractClass
import org.jetbrains.plugins.scala.lang.psi.types.api.designator._
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils

class ExhaustiveMatchCompletionContributor extends ScalaCompletionContributor {

  import ExhaustiveMatchCompletionContributor._

  extend(CompletionType.BASIC,
    PlatformPatterns.psiElement.inside(classOf[ScPostfixExpr]),
    new ScalaCompletionProvider {
      override protected def completionsFor(position: PsiElement)
                                           (implicit parameters: CompletionParameters,
                                            context: ProcessingContext): Iterable[LookupElement] =
        for {
          place@ScPostfixExpr(Typeable(PatternGenerationStrategy(strategy)), _) <- position.findContextOfType(classOf[ScPostfixExpr])
        } yield LookupElementBuilder.create(ItemText)
          .withInsertHandler(createInsertHandler(strategy)(place))
          .withRenderer(createRenderer)
    }
  )
}

object ExhaustiveMatchCompletionContributor {

  import ScalaKeyword._

  private[lang] val ItemText: String = MATCH
  private[lang] val RendererTailText = " (exhaustive)"

  private object PatternGenerationStrategy {

    def unapply(`type`: ScType): Option[PatternGenerationStrategy] = {
      val valueType = `type`.extractDesignatorSingleton.getOrElse(`type`)

      valueType match {
        case ScProjectionType(designator@DesignatorOwner(enumeration@ScalaEnumeration()), _) =>
          Some(new ScalaEnumGenerationStrategy(valueType, designator, enumeration.members))
        case ScDesignatorType(enum: PsiClass) if enum.isEnum =>
          Some(new JavaEnumGenerationStrategy(valueType, enum.getFields))
        case ExtractClass(SealedDefinition(classes)) =>
          val inheritors = Inheritors(classes)
          if (inheritors.namedInheritors.isEmpty && inheritors.anonymousInheritors.isEmpty) None
          else Some(new SealedClassGenerationStrategy(valueType, inheritors))
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

  private sealed abstract class PatternGenerationStrategy protected(protected val valueType: ScType) {

    final def replacement(implicit place: PsiElement): String =
      patterns.map {
        case (pattern, _) => s"$CASE $pattern ${ScalaPsiUtil.functionArrow}"
      }.mkString(s"$MATCH {\n", "\n", "\n}")

    protected def patterns(implicit place: PsiElement): Seq[NameAndElement]
  }

  private class SealedClassGenerationStrategy(valueType: ScType,
                                              inheritors: Inheritors)
    extends PatternGenerationStrategy(valueType) {

    override protected def patterns(implicit place: PsiElement): Seq[NameAndElement] =
      inheritors.patterns()
  }

  private class JavaEnumGenerationStrategy(valueType: ScType,
                                           fields: Array[PsiField])
    extends PatternGenerationStrategy(valueType) {

    override protected def patterns(implicit place: PsiElement): Seq[NameAndElement] = {
      val typeText = valueType.presentableText
      fields.collect {
        case constant: PsiEnumConstant => s"$typeText.${constant.getName}" -> constant
      }
    }
  }

  private class ScalaEnumGenerationStrategy(valueType: ScType,
                                            designator: ScType,
                                            members: Seq[ScMember])
    extends PatternGenerationStrategy(valueType) {

    override protected def patterns(implicit place: PsiElement): Seq[NameAndElement] =
      members.filter {
        ResolveUtils.isAccessible(_, place, forCompletion = true)
      }.collect {
        case value: ScValue if isEnumerationValue(value) => value
      }.flatMap {
        declaredNamesPatterns(_, designator)
      }

    private def isEnumerationValue(value: ScValue) =
      value.`type`().exists(_.conforms(valueType))
  }

  private def createInsertHandler(strategy: PatternGenerationStrategy)
                                 (implicit place: PsiElement) = new InsertHandler[LookupElement] {

    override def handleInsert(insertionContext: InsertionContext, lookupElement: LookupElement): Unit = {
      val InsertionContextExt(editor, document, file, project) = insertionContext
      val startOffset = insertionContext.getStartOffset

      if (file.findElementAt(startOffset) != null) {
        val replacement = strategy.replacement

        document.replaceString(startOffset, insertionContext.getSelectionEndOffset, replacement)
        CodeStyleManager.getInstance(project)
          .reformatText(file, startOffset, startOffset + replacement.length)
        insertionContext.commitDocument()

        val clauses = file.findElementAt(startOffset).getParent match {
          case statement: ScMatchStmt => statement.getCaseClauses
        }

        val sibling = clauses.caseClause.getNextSibling match {
          case null => clauses.getNextSibling
          case element => element
        }

        sibling match {
          case whiteSpace: PsiWhiteSpaceImpl =>
            val caretOffset = whiteSpace.getStartOffset + 1
            whiteSpace.replaceWithText(" " + whiteSpace.getText)

            PsiDocumentManager.getInstance(project)
              .doPostponedOperationsAndUnblockDocument(document)
            editor.getCaretModel.moveToOffset(caretOffset)
          case _ =>
        }
      }
    }
  }

  private def createRenderer = new LookupElementRenderer[LookupElement] {

    override def renderElement(lookupElement: LookupElement, presentation: LookupElementPresentation): Unit = {
      presentation.setItemText(lookupElement.getLookupString)
      presentation.setItemTextBold(true)
      presentation.setTailText(RendererTailText, true)
    }
  }
}
