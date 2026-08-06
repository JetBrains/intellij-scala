package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{LookupElementBuilder, LookupElementDecorator}
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.codeInsight.intention.types.{AbstractTypeAnnotationIntention, AddOnlyStrategy, ChooseTypeTextExpression}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScValueOrVariableDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

import scala.collection.mutable

final class ScalaTypeAnnotationsCompletionContributor extends CompletionContributor {

  import ScalaTypeAnnotationsCompletionContributor._

  private val provider = new ScalaTypeAnnotationsCompletionProvider
  private val parentClasses = Seq(
    classOf[ScFunctionDefinition],
    classOf[ScPatternDefinition],
    classOf[ScVariableDefinition]
  )

  for (parentClass <- parentClasses) {
    extend(CompletionType.BASIC, pattern(parentClass), provider)
  }
}

object ScalaTypeAnnotationsCompletionContributor {
  private def pattern(parentClass: Class[_ <: ScalaPsiElement]) =
    identifierPattern
      .withParent(psiElement(classOf[ScStableCodeReference])
        .withParent(psiElement(classOf[ScSimpleTypeElement])
          .withParent(parentClass)
          .withPrevSiblingNotWhitespace(ScalaTokenTypes.tCOLON)
          .withNextSiblingNotWhitespaceComment(ScalaTokenTypes.tASSIGN)
        )
      )

  private class ScalaTypeAnnotationsCompletionProvider extends CompletionProvider[CompletionParameters] {
    override def addCompletions(parameters: CompletionParameters,
                                context: ProcessingContext,
                                resultSet: CompletionResultSet): Unit = {
      val place = positionFromParameters(parameters)

      import AbstractTypeAnnotationIntention._

      val lookupElementsBuffer = mutable.ListBuffer.empty[LookupElementBuilder]
      val strategy = new AddTypeLookupElementsStrategy(lookupElementsBuffer)

      functionParent(place).foreach(function => strategy.functionWithoutType(fnWithoutTypeElement(function)))
      valueParent(place).foreach(value => strategy.valueWithoutType(valOrVarWithoutTypeElement(value)))
      variableParent(place).foreach(variable => strategy.variableWithoutType(valOrVarWithoutTypeElement(variable)))

      val items = lookupElementsBuffer.result().map(TypeAnnotationsLookupItem(_))
      if (items.nonEmpty) {
        resultSet.addAllElements(items)
      }
    }
  }

  private object TypeAnnotationsLookupItem {
    def apply(builder: LookupElementBuilder): LookupElementDecorator[LookupElementBuilder] =
      LookupElementDecorator.withDelegateInsertHandler(builder, ReformattingInsertHandler)

    private object ReformattingInsertHandler extends InsertHandler[LookupElementBuilder] {
      override def handleInsert(context: InsertionContext, item: LookupElementBuilder): Unit = {
        item.handleInsert(context)
        context.commitDocument()

        val file = context.getFile
        for {
          start <- file.findElementAt(context.getStartOffset - 1).toOption
          end <- file.findElementAt(context.getTailOffset - 1).toOption
          parent <- PsiTreeUtil.findCommonContext(start, end).toOption
        } CodeStyleManager.getInstance(context.getProject).reformat(parent)
      }
    }
  }

  private final class AddTypeLookupElementsStrategy(lookupElems: mutable.Growable[LookupElementBuilder]) extends AddOnlyStrategy {
    override def addTypeAnnotation(types: Seq[AddOnlyStrategy.TypeForAnnotation], context: PsiElement, anchor: PsiElement): Unit = {
      val variants = Seq.from(AddOnlyStrategy.typeAnnotationWithVariants(types, context))
        .flatMap(_.validVariants)
      if (variants.isEmpty) return

      val expr = new ChooseTypeTextExpression(variants)
      lookupElems ++= expr.calcLookupElements()
    }
  }

  private def fnWithoutTypeElement(function: ScFunctionDefinition): ScFunctionDefinition =
    withoutTypeElement[ScFunctionDefinition](function, _.returnTypeElement)

  private def valOrVarWithoutTypeElement[T <: ScValueOrVariableDefinition](value: T): T =
    withoutTypeElement[T](value, _.typeElement)

  private def withoutTypeElement[T <: ScMember](m: T, fn: T => Option[ScTypeElement]): T = {
    val member = m.copy().asInstanceOf[T]
    for {
      colon <- member.children.find(_.elementType == ScalaTokenTypes.tCOLON)
      typeElement <- fn(member)
    } member.deleteChildRange(colon, typeElement)
    member
  }
}
