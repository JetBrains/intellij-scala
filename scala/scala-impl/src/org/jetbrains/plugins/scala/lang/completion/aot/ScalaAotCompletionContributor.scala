package org.jetbrains.plugins.scala
package lang
package completion
package aot

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup._
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, ScParameterType, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueDeclaration, ScVariableDeclaration}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

import scala.collection.mutable

/**
  * @author Pavel Fatin
  */
final class ScalaAotCompletionContributor extends ScalaCompletionContributor {

  import ScalaAotCompletionContributor._

  extend(
    new ParameterCompletionProvider {

      override protected def createConsumer(resultSet: CompletionResultSet, position: PsiElement): Consumer = new TypedConsumer(resultSet) {

        private val maybeRange = for {
          parameter <- position.parentOfType(classOf[ScParameter])

          typeElement <- findTypeElement(parameter)
          bound = typeElement.getTextRange.getEndOffset

          identifier <- findIdentifier(parameter)
          origin = identifier.getTextRange.getEndOffset
        } yield new TextRange(origin, bound)

        override protected def createInsertHandler(itemText: String): InsertHandler = new InsertHandler(itemText) {

          private val delta = itemText.indexOf(Delimiter) + Delimiter.length

          override def handleInsert(context: InsertionContext, decorator: aot.Decorator): Unit = {
            def withRange(function: (Int, Int) => Unit): Unit =
              maybeRange.foreach { range =>
                function(context.getTailOffset, range.getLength)
              }

            withRange {
              case (tailOffset, length) => context.setTailOffset(tailOffset + length)
            }

            super.handleInsert(context, decorator)

            updateStartOffset(context.getOffsetMap)
            decorator.getDelegate.handleInsert(context)

            withRange {
              case (tailOffset, _) => context.getEditor.getCaretModel.moveToOffset(tailOffset)
            }
          }

          private def updateStartOffset(offsetMap: OffsetMap): Unit = {
            import CompletionInitializationContext.START_OFFSET
            val startOffset = offsetMap.getOffset(START_OFFSET) + delta
            offsetMap.addOffset(START_OFFSET, startOffset)
          }
        }
      }
    },
    parentTypes = classOf[ScParameter], classOf[ScParameterClause], classOf[ScParameters], classOf[ScFunction]
  )

  extend(
    new ParameterCompletionProvider {

      override protected def createConsumer(resultSet: CompletionResultSet, position: PsiElement): Consumer = new UntypedConsumer(resultSet)
    },
    parentTypes = classOf[ScParameter], classOf[ScParameterClause], classOf[ScParameters], classOf[ScFunctionExpr]
  )

  extend(
    new CompletionProvider[ScValueDeclaration] {

      override protected def addCompletions(resultSet: CompletionResultSet,
                                            prefix: String)
                                           (implicit parameters: CompletionParameters,
                                            context: ProcessingContext): Unit =
        positionFromParameters.getContext.getContext.getContext match {
          case _: ScVariableDeclaration | _: ScValueDeclaration => super.addCompletions(resultSet, prefix)
          case _ =>
        }

      override protected def createElement(text: String,
                                           context: PsiElement,
                                           child: PsiElement): ScValueDeclaration =
        ScalaPsiElementFactory.createDeclarationFromText(ScalaKeyword.VAL + " " + text, context, child)
          .asInstanceOf[ScValueDeclaration]

      override protected def findTypeElement(declaration: ScValueDeclaration): Option[ScTypeElement] =
        declaration.typeElement

      override protected def findContext(element: ScValueDeclaration): PsiElement =
        super.findContext(element).getContext.getContext

      override protected def createConsumer(resultSet: CompletionResultSet, position: PsiElement): Consumer = new UntypedConsumer(resultSet) {

        private val consumed = mutable.Set.empty[String]

        override protected def consume(lookupElement: LookupElement, itemText: String): Unit = {
          if (consumed.add(itemText)) super.consume(lookupElement, itemText)
        }
      }
    },
    parentTypes = classOf[ScFieldId]
  )

  private def extend(provider: CompletionProvider[_ <: ScalaPsiElement],
                     parentTypes: Class[_ <: ScalaPsiElement]*): Unit = extend(
    CompletionType.BASIC,
    PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER).withParents(parentTypes: _*),
    provider
  )
}

object ScalaAotCompletionContributor {

  private trait ParameterCompletionProvider extends CompletionProvider[ScParameter] {

    override protected def createElement(text: String,
                                         context: PsiElement,
                                         child: PsiElement): ScParameter =
      ScalaPsiElementFactory.createParamClausesWithContext(text.parenthesize(), context, child)
        .params.head

    override protected def findTypeElement(parameter: ScParameter): Option[ScParameterType] =
      parameter.paramType
  }

}
