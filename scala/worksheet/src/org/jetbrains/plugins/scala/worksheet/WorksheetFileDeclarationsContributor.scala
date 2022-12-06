package org.jetbrains.plugins.scala.worksheet

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.{PsiElement, ResolveState}
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.FileDeclarationsContributor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.resolve.processor.ResolveProcessor
import org.jetbrains.plugins.scala.worksheet.actions.repl.ResNUtils

/**
 * The contributor is designed to provide synthetic declarations for vals which are created by Scala REPL at runtime<br>
 * REPL creates `resN` values for each expression consumed by it
 *
 * @example We want to be able to reference `res1` in this code {{{
 *   <worksheet code>     <worksheet output>
 *   def foo = 42       | def foo: Int
 *   foo                | val res0: Int = 42
 *   println()          |
 *   23                 | val res1: Int = 23
 *   res1               | val res2: Int = 23
 * }}}
 *
 * @see SCL-14198
 */
final class WorksheetFileDeclarationsContributor extends FileDeclarationsContributor {

  override def accept(holder: PsiElement): Boolean =
    holder match {
      case file: WorksheetFile => ResNUtils.isResNSupportedInFile(file)
      case _ => false
    }

  override def processAdditionalDeclarations(processor: PsiScopeProcessor, holder: PsiElement, state: ResolveState, lastParent: PsiElement): Unit = {
    //Offset of the most top-level parent of the element which is being resolved
    //This is needed to restrict the scope of resolve to avoid SOE, see SCL-20478
    val topLevelExpressionOffset: Int = {
      val element = processor match {
        case resolveProcessor: ResolveProcessor =>
          resolveProcessor.ref
        case _ =>
          return
      }

      val topLevelExpr = element.withParentsInFile.find(_.getParent.isInstanceOf[WorksheetFile])
      topLevelExpr match {
        case Some(expr) => expr.getNode.getTextRange.getStartOffset
        case _ =>
          return
      }
    }

    var currResIndex = 0

    val children = holder.getChildren
    val expressions = children.iterator
      .filterByType[ScExpression]
      .takeWhile(_.getNode.getStartOffset < topLevelExpressionOffset)

    var continueLoop = true
    while (continueLoop && expressions.hasNext) {
      ProgressManager.checkCanceled()

      val expr = expressions.next()
      val exprText = expr.getText

      val isCompletionInvokedOnElement = exprText.contains(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)
      if (!isCompletionInvokedOnElement) {
        val resNName = s"res$currResIndex"

        val expressionContainsReferencesToResN: Boolean = {
          val allResNMatches = ResNUtils.ResNRegex.findAllMatchIn(exprText)
          val indexes = allResNMatches.filter(_.matched == resNName).map(_.start)
          val exprStartOffset = expr.getTextRange.getStartOffset
          indexes.exists { idx =>
            val element = holder.findElementAt(exprStartOffset + idx + 1)
            element != null && element.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER
          }
        }

        if (!expressionContainsReferencesToResN) {
          val syntheticDefText = s" val $resNName = $exprText"
          val syntheticDef = ScalaPsiElementFactory.createDefinitionWithContext(syntheticDefText, holder, expr)
          syntheticDef match {
            case patternDef: ScPatternDefinition =>
              val declaredElements = patternDef.declaredElements
              declaredElements.foreach { declared =>
                GotoOriginalHandlerUtil.setGoToTarget2(declared, expr)

                val continue = processor.execute(declared, state)
                continueLoop = continue
              }
            case _ =>
          }
        }

        currResIndex += 1
      }
    }
  }
}
