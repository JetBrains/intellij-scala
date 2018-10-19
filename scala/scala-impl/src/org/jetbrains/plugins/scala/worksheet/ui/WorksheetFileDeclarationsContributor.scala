package org.jetbrains.plugins.scala.worksheet.ui

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.{PsiElement, ResolveState}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaFileImpl, ScalaPsiElementFactory}
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings
import org.jetbrains.plugins.scala.worksheet.{FileDeclarationsContributor, GotoOriginalHandlerUtil}

import scala.collection.mutable.ArrayBuffer

/**
  * User: Dmitry.Naydanov
  * Date: 02.08.18.
  */
class WorksheetFileDeclarationsContributor extends FileDeclarationsContributor {
  override def accept(holder: PsiElement): Boolean = holder match {
    case scalaFile: ScalaFileImpl if scalaFile.isWorksheetFile => 
      WorksheetFileSettings.isReplLight(scalaFile) || WorksheetFileSettings.isRepl(scalaFile)
    case _ => false
  }

  override def processAdditionalDeclarations(processor: PsiScopeProcessor, holder: PsiElement, state: ResolveState): Unit = {
    var ind = 0

    holder.getChildren foreach {
      case expr: ScExpression =>
        val text = expr.getText

        if (!text.contains(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)) {
          val name = s"res$ind"
          val inds = ArrayBuffer[Int]()
          val m = name.r.pattern.matcher(text)
          while (m.find()) {
            inds += m.start()
          }

          val skip = inds exists {
            idx => holder.findElementAt(expr.getTextRange.getStartOffset + idx + 1) match {
              case psi: PsiElement if psi.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER =>
                true
              case _ => false
            }
          }

          if (!skip) ScalaPsiElementFactory.createDefinitionWithContext(s" val res$ind = $text", holder, expr) match {
            case patternDef: ScPatternDefinition =>
              patternDef.declaredElements foreach {
                declared =>
                  GotoOriginalHandlerUtil.storeNonModifiablePsi(declared, expr)
                  if (!processor.execute(declared, state)) return 
              }
            case _ =>
          }
          ind += 1
        }
      case _ =>
    }
  }
}
