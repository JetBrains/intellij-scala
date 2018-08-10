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
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClauses, ScTypedPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ExtractClass
import org.jetbrains.plugins.scala.lang.psi.types.api.designator._
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiUtil, TypeAdjuster}
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils

class ExhaustiveMatchCompletionContributor extends ScalaCompletionContributor {

  import ExhaustiveMatchCompletionContributor._

  extend(
    CompletionType.BASIC,
    PlatformPatterns.psiElement.inside(classOf[ScSugarCallExpr]),
    new ScalaCompletionProvider {
      override protected def completionsFor(position: PsiElement)
                                           (implicit parameters: CompletionParameters,
                                            context: ProcessingContext): Iterable[LookupElement] =
        for {
          sugarCall <- position.findContextOfType(classOf[ScSugarCallExpr])
          if !sugarCall.isInstanceOf[ScPrefixExpr]

          ScSugarCallExpr(Typeable(operandType), operation, _) <- Some(sugarCall)
          if operation.isAncestorOf(position)

          PatternGenerationStrategy(strategy) <- Some(operandType)
        } yield LookupElementBuilder.create(ItemText)
          .withInsertHandler(createInsertHandler(strategy)(sugarCall))
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
      }.map { name =>
        new StablePatternComponents(enumeration, qualifiedName, name)
      }
    }

    private def isEnumerationValue(value: ScValue) =
      value.`type`().exists(_.conforms(valueType))
  }

  private def createInsertHandler(strategy: PatternGenerationStrategy)
                                 (implicit place: PsiElement) = new InsertHandler[LookupElement] {

    override def handleInsert(insertionContext: InsertionContext, lookupElement: LookupElement): Unit =
      insertionContext.getFile.findElementAt(insertionContext.getStartOffset) match {
        case null =>
        case element => handleInsert(element)(insertionContext)
      }

    private def handleInsert(element: PsiElement)
                            (implicit insertionContext: InsertionContext): Unit = {
      val patterns = strategy.patterns
      replaceTextPhase(patterns)

      val whiteSpace = element.getParent match {
        case statement: ScMatchStmt => replacePsiPhase(patterns, statement.getCaseClauses)
      }
      moveCaretPhase(whiteSpace)
    }

    private def replaceTextPhase(patterns: Seq[PatternComponents])
                                (implicit insertionContext: InsertionContext): Unit = {
      val replacement = patterns.map { pattern =>
        s"$CASE $pattern ${ScalaPsiUtil.functionArrow}"
      }.mkString(s"$MATCH {\n", "\n", "\n}")

      val startOffset = insertionContext.getStartOffset
      val InsertionContextExt(_, document, file, project) = insertionContext

      document.replaceString(
        startOffset,
        insertionContext.getSelectionEndOffset,
        replacement
      )
      CodeStyleManager.getInstance(project).reformatText(
        file,
        startOffset,
        startOffset + replacement.length
      )
      insertionContext.commitDocument()
    }

    private def replacePsiPhase(patterns: Seq[PatternComponents],
                                clauses: ScCaseClauses)
                               (implicit insertionContext: InsertionContext): PsiWhiteSpaceImpl = inWriteCommandAction {
      val caseClauses = clauses.caseClauses

      def typedPatterns = for {
        caseClause <- caseClauses
        pattern@ScTypedPattern(typeElement) <- caseClause.pattern
      } yield (pattern, typeElement)

      TypeAdjuster.adjustFor(
        typedPatterns.map(_._2),
        addImports = strategy.isInstanceOf[SealedClassGenerationStrategy],
        useTypeAliases = false
      )

      for {
        ((pattern, typeElement), components) <- typedPatterns.zip(patterns)
      } {
        val patternText = replacementText(typeElement, components)
        val replacement = ScalaPsiElementFactory.createPatternFromTextWithContext(patternText, place.getContext, place)
        pattern.replace(replacement)
      }

      caseClauses.head.nextSibling.getOrElse(clauses.getNextSibling) match {
        case whiteSpace: PsiWhiteSpaceImpl =>
          whiteSpace.replaceWithText(" " + whiteSpace.getText).asInstanceOf[PsiWhiteSpaceImpl]
      }
    }(insertionContext.getProject)

    private def moveCaretPhase(whiteSpace: PsiWhiteSpaceImpl)
                              (implicit insertionContext: InsertionContext): Unit = {
      val InsertionContextExt(editor, document, _, project) = insertionContext

      PsiDocumentManager.getInstance(project)
        .doPostponedOperationsAndUnblockDocument(document)
      editor.getCaretModel.moveToOffset(whiteSpace.getStartOffset + 1)
    }

    private def replacementText(typeElement: ScTypeElement, components: PatternComponents): String =
      (typeElement, components) match {
        case (simpleTypeElement@SimpleTypeReferenceText(referenceText), _) if simpleTypeElement.singleton => referenceText
        case (_, TypedPatternComponents(constructor)) =>
          val referenceText = typeElement match {
            case SimpleTypeReferenceText(text) => text
            case ScParameterizedTypeElement(SimpleTypeReferenceText(text), _) => text
          }

          referenceText + constructor.effectiveFirstParameterSection.map { parameter =>
            parameter.name + (if (parameter.isVarArgs) "@_*" else "")
          }.commaSeparated(model = Model.Parentheses)
        case _ =>
          val name = typeElement.`type`().toOption
            .flatMap(NameSuggester.suggestNamesByType(_).headOption)
            .getOrElse(WildcardPatternComponents.toString)
          s"$name: ${typeElement.getText}"
      }

    private object SimpleTypeReferenceText {

      def unapply(typeElement: ScSimpleTypeElement): Option[String] =
        typeElement.reference.map(_.getText)
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
