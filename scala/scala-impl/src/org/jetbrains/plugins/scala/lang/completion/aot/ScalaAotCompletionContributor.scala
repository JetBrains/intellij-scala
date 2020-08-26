package org.jetbrains.plugins.scala
package lang
package completion
package aot

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup._
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScFieldId, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._

/**
 * @author Pavel Fatin
 */
final class ScalaAotCompletionContributor extends ScalaCompletionContributor {

  import CompletionType.BASIC
  import ScalaAotCompletionContributor._

  import reflect.{ClassTag, classTag}

  //noinspection ConvertExpressionToSAM
  registerParameterProvider[ScFunction](
    new ParameterCompletionProvider {

      //noinspection TypeAnnotation
      override protected def createConsumer(resultSet: CompletionResultSet, position: PsiElement) = new TypedConsumer(resultSet) {

        override protected def createInsertHandler(itemText: String): aot.InsertHandler = new aot.InsertHandler(itemText) {

          override def handleInsert(decorator: Decorator)
                                   (implicit context: InsertionContext): Unit = {
            super.handleInsert(decorator)
            inReplaceMode { context =>
              context.getEditor.getCaretModel.moveToOffset(context.getTailOffset)
            }
          }

          override protected def handleReplace(implicit context: InsertionContext): Unit = {
            inReplaceMode { context =>
              for {
                parameter <- position.parentOfType(classOf[ScParameter])

                typeElement <- findTypeElement(parameter)
                bound = typeElement.getTextRange.getEndOffset

                identifier <- findIdentifier(parameter)
                origin = identifier.getTextRange.getEndOffset

                length = bound - origin
              } context.offsetMap(InsertionContext.TAIL_OFFSET) += length
            }

            super.handleReplace

            val delta = itemText.indexOf(Delimiter) + Delimiter.length
            context.offsetMap(CompletionInitializationContext.START_OFFSET) += delta
          }

          private def inReplaceMode(action: InsertionContext => Unit)
                                   (implicit context: InsertionContext): Unit =
            context.getCompletionChar match {
              case Lookup.REPLACE_SELECT_CHAR => action(context)
              case _ =>
            }
        }
      }
    }
  )

  registerParameterProvider[ScPrimaryConstructor](
    new ParameterCompletionProvider {

      override protected def createConsumer(resultSet: CompletionResultSet, position: PsiElement) = new TypedConsumer(resultSet)

      override protected def createElement(text: String,
                                           context: PsiElement,
                                           child: PsiElement): ScParameter =
        createClassParamClausesWithContext(text.parenthesize(), context)
          .params.head
    }
  )

  registerParameterProvider[ScFunctionExpr](
    (resultSet: CompletionResultSet, _: PsiElement) => new UntypedConsumer(resultSet)
  )

  registerDeclarationProvider[ScFieldId](
    new DeclarationCompletionProvider[ScValueDeclaration](ScalaKeyword.VAL, classOf[ScValueDeclaration], classOf[ScVariableDeclaration]) {

      override protected def findTypeElement(declaration: ScValueDeclaration): Option[ScTypeElement] =
        declaration.typeElement

      override protected def findContext(element: ScValueDeclaration): PsiElement =
        super.findContext(element).getContext.getContext

    }
  )

  registerDeclarationProvider[ScFunctionDeclaration](
    new DeclarationCompletionProvider[ScFunctionDeclaration](ScalaKeyword.DEF, classOf[ScFunctionDeclaration]) {

      override protected def findTypeElement(element: ScFunctionDeclaration): Option[ScTypeElement] =
        element.returnTypeElement
    }
  )

  private def registerParameterProvider[E <: ScalaPsiElement : ClassTag](provider: ParameterCompletionProvider): Unit = extend(
    BASIC,
    identifierPattern.withParents(classOf[ScParameter], classOf[ScParameterClause], classOf[ScParameters], classTag[E].runtimeClass.asSubclass(classOf[ScalaPsiElement])),
    provider
  )

  private def registerDeclarationProvider[E <: ScalaPsiElement : ClassTag](provider: DeclarationCompletionProvider[_]): Unit = extend(
    BASIC,
    identifierWithParentPattern(classTag[E].runtimeClass.asSubclass(classOf[ScalaPsiElement])),
    provider
  )
}

object ScalaAotCompletionContributor {

  private trait ParameterCompletionProvider extends aot.CompletionProvider[ScParameter] {

    override protected def createElement(text: String,
                                         context: PsiElement,
                                         child: PsiElement): ScParameter =
      createParamClausesWithContext(text.parenthesize(), context, child)
        .params.head

    override protected def findTypeElement(parameter: ScParameter): Option[ScTypeElement] =
      parameter.paramType.map(_.typeElement)
  }

  private abstract class DeclarationCompletionProvider[D <: ScMember with ScDeclaration](keyword: String,
                                                                                         classes: Class[_ <: ScMember]*) extends aot.CompletionProvider[D] {

    override protected def addCompletions(resultSet: CompletionResultSet, prefix: String)
                                         (implicit parameters: CompletionParameters, context: ProcessingContext): Unit =
      PsiTreeUtil.getParentOfType(positionFromParameters, classes: _*) match {
        case member: ScMember if !member.hasModifierPropertyScala(ScalaModifier.OVERRIDE) =>
          super.addCompletions(resultSet, prefix)
        case _ =>
      }

    override protected final def createElement(text: String, context: PsiElement, child: PsiElement): D =
      createDeclarationFromText(keyword + " " + text, context, child).asInstanceOf[D]

    override protected final def createConsumer(resultSet: CompletionResultSet, position: PsiElement) = new UntypedConsumer(resultSet)
  }
}
