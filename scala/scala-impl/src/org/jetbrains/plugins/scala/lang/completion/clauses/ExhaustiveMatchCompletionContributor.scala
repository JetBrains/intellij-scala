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
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMatchStmt, ScPostfixExpr}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api.ExtractClass
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

class ExhaustiveMatchCompletionContributor extends ScalaCompletionContributor {

  import ExhaustiveMatchCompletionContributor._

  extend(CompletionType.BASIC,
    PlatformPatterns.psiElement.inside(classOf[ScPostfixExpr]),
    new ScalaCompletionProvider {
      override protected def completionsFor(position: PsiElement)
                                           (implicit parameters: CompletionParameters, context: ProcessingContext): Iterable[LookupElement] =
        for {
          place@ScPostfixExpr(Typeable(ExtractClass(clazz)), _) <- position.findContextOfType(classOf[ScPostfixExpr])
          strategy <- createStrategy(clazz)
        } yield LookupElementBuilder.create(ItemText)
          .withInsertHandler(createInsertHandler(strategy)(place))
          .withRenderer(createRenderer)
    }
  )
}

object ExhaustiveMatchCompletionContributor {

  import ScalaTokenTypes._

  private[lang] val ItemText: String = kMATCH
  private[lang] val RendererTailText = " (exhaustive)"

  private def createStrategy(clazz: PsiClass): Option[PatternGenerationStrategy] = clazz match {
    case definition: ScTypeDefinition if definition.isSealed => Some(new SealedClassGenerationStrategy(definition))
    case _ if clazz.isEnum => Some(new EnumGenerationStrategy(clazz))
    case _ if !clazz.hasFinalModifier => Some(new NonFinalClassGenerationStrategy(clazz))
    case _ => None
  }

  private sealed abstract class PatternGenerationStrategy protected(protected val clazz: PsiClass) {

    final def replacement(implicit place: PsiElement): String = {
      val clauses = patterns.map { pattern =>
        s"$kCASE $pattern $tFUNTYPE"
      }.mkString("\n")

      s"""$kMATCH $tLBRACE
         |$clauses
         |$tRBRACE""".stripMargin
    }

    protected def patterns(implicit place: PsiElement): Seq[String] =
      inheritors.flatMap(patternTexts)

    protected def inheritors: Seq[ScTypeDefinition] = findInheritors(clazz)
  }

  private class SealedClassGenerationStrategy(definition: ScTypeDefinition)
    extends PatternGenerationStrategy(definition)

  private class EnumGenerationStrategy(clazz: PsiClass)
    extends PatternGenerationStrategy(clazz) {

    override def patterns(implicit place: PsiElement): Seq[String] = {
      val className = clazz.name
      clazz.getFields.collect {
        case constant: PsiEnumConstant => s"$className.${constant.name}"
      }.toSeq
    }
  }

  private class NonFinalClassGenerationStrategy(clazz: PsiClass)
    extends PatternGenerationStrategy(clazz) {

    override def patterns(implicit place: PsiElement): Seq[String] =
      super.patterns :+ (tUNDER: String)

    override protected def inheritors: Seq[ScTypeDefinition] = {
      val inheritors = super.inheritors
      val classes = clazz match {
        case scalaClass: ScTypeDefinition => scalaClass +: inheritors
        case _ => inheritors
      }

      classes.filter { definition =>
        definition.isCase ||
          definition.isObject ||
          findExtractor(definition).isDefined
      }
    }
  }

  private def createInsertHandler(strategy: PatternGenerationStrategy)
                                 (implicit place: PsiElement) = new InsertHandler[LookupElement] {

    override def handleInsert(context: InsertionContext, lookupElement: LookupElement): Unit = {
      val InsertionContextExt(editor, document, file, project) = context
      val startOffset = context.getStartOffset

      if (file.findElementAt(startOffset) != null) {
        val replacement = strategy.replacement

        document.replaceString(startOffset, context.getSelectionEndOffset, replacement)
        CodeStyleManager.getInstance(project)
          .reformatText(file, startOffset, startOffset + replacement.length)
        context.commitDocument()

        val clauses = file.findElementAt(startOffset).getParent match {
          case statement: ScMatchStmt => statement.getCaseClauses
        }

        val sibling = clauses.caseClause.getNextSibling match {
          case null => clauses.getNextSibling
          case element => element
        }

        sibling match {
          case ws: PsiWhiteSpaceImpl =>
            val newWhiteSpace = ws.replaceWithText(" " + ws.getText)
            val caretOffset = newWhiteSpace.getStartOffset + 1

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
