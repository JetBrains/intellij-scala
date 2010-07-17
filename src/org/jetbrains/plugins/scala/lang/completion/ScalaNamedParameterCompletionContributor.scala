package org.jetbrains.plugins.scala.lang.completion

import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScReferenceExpression, ScArgumentExprList}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.patterns.{ElementPattern, PlatformPatterns}
import com.intellij.codeInsight.completion._
import com.intellij.util.ProcessingContext
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.plugins.scala.lang.psi.types.ScSubstitutor
import com.intellij.psi.PsiElement
/**
 * @author Alexander Podkhalyuzin
 */

class ScalaNamedParameterCompletionContributor extends CompletionContributor {
  private def superParentPattern(clazz: java.lang.Class[_ <: PsiElement]): ElementPattern[PsiElement] = {
    PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER).withParent(classOf[ScReferenceExpression]).
            withSuperParent(2, clazz)
  }

  override def beforeCompletion(context: CompletionInitializationContext) = {
    context.setFileCopyPatcher(new DummyIdentifierPatcher("IntelliJIDEARulezzz = IntelliJIDEARulezzzR"))
  }

  //method call named parameters
  extend(CompletionType.BASIC, superParentPattern(classOf[ScArgumentExprList]),
    new CompletionProvider[CompletionParameters] {
      def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val element = parameters.getPosition
        val ref = element.getParent.asInstanceOf[ScReferenceExpression]
        if (ref.qualifier != None) return
        for (variant <- ref.getVariants(false, true)) {
          variant match {
            case (el: LookupElement, elem: PsiElement, subst: ScSubstitutor) => {
              result.addElement(el)
            }
            case _ =>
          }
        }
      }
    })
}