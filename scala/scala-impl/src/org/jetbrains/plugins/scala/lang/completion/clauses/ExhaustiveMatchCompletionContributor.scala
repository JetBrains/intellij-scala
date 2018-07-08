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
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ExtractClass
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

class ExhaustiveMatchCompletionContributor extends ScalaCompletionContributor {

  import ExhaustiveMatchCompletionContributor._

  extend(CompletionType.BASIC,
    PlatformPatterns.psiElement.inside(classOf[ScPostfixExpr]),
    new ScalaCompletionProvider {
      override protected def completionsFor(position: PsiElement)
                                           (implicit parameters: CompletionParameters, context: ProcessingContext): Iterable[LookupElement] =
        for {
          place@ScPostfixExpr(Typeable(scType), _) <- position.findContextOfType(classOf[ScPostfixExpr])
          strategy <- createStrategy(scType)
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

  private def createStrategy(`type`: ScType): Option[PatternGenerationStrategy] = `type` match {
    case ScalaEnumGenerationStrategy.IsEnumerationValueType(enum, valueType) =>
      Some(new ScalaEnumGenerationStrategy(enum, valueType))
    case ExtractClass(clazz) =>
      clazz match {
        case definition: ScTypeDefinition if definition.isSealed => Some(new SealedClassGenerationStrategy(definition))
        case enum if enum.isEnum => Some(new JavaEnumGenerationStrategy(enum))
        case _ if !clazz.hasFinalModifier => Some(new NonFinalClassGenerationStrategy(clazz))
        case _ => None
      }
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

  private abstract class EnumGenerationStrategy(enum: PsiClass)
    extends PatternGenerationStrategy(enum) {

    override def patterns(implicit place: PsiElement): Seq[String] = {
      val className = enum.name
      definedNames.map(name => s"$className.$name")
    }

    protected def definedNames: Seq[String]
  }

  private class JavaEnumGenerationStrategy(enum: PsiClass)
    extends EnumGenerationStrategy(enum) {

    override protected def definedNames: Seq[String] =
      enum.getFields.collect {
        case constant: PsiEnumConstant => constant.name
      }
  }

  private class ScalaEnumGenerationStrategy(enum: ScObject,
                                            valueType: ScType)
    extends EnumGenerationStrategy(enum) {

    override protected def definedNames: Seq[String] =
      enum.members.collect {
        case value: ScValue if isEnumerationValue(value) => value
      }.flatMap(_.declaredNames)

    private def isEnumerationValue(value: ScValue) =
      value.isPublic && value.`type`().exists(_.equiv(valueType))
  }

  private object ScalaEnumGenerationStrategy {

    object IsEnumerationValueType {

      def unapply(`type`: ScType): Option[(ScObject, ScType)] = `type` match {
        case ScProjectionType(ScDesignatorType(enum@IsEnumeration()), _) => Some(enum, `type`)
        case _ => None
      }

      private object IsEnumeration {

        private val EnumerationFQN = "scala.Enumeration"

        def unapply(scalaObject: ScObject): Boolean =
          scalaObject.supers.map(_.qualifiedName).contains(EnumerationFQN)
      }

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
