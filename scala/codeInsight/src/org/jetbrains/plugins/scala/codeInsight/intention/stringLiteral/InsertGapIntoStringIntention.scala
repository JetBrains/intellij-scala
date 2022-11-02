package org.jetbrains.plugins.scala.codeInsight.intention.stringLiteral

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.{tMULTILINE_STRING, tSTRING}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral

final class InsertGapIntoStringIntention extends PsiElementBaseIntentionAction {

  import InsertGapIntoStringIntention._

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val replacement = getReplacement(editor, element)
    replacement.isDefined
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val replacement = getReplacement(editor, element)
    replacement.foreach {
      case (text, caretMove) => IntentionPreviewUtils.write { () =>
        val caretModel = editor.getCaretModel
        editor.getDocument.insertString(caretModel.getOffset, text)
        caretModel.moveCaretRelatively(caretMove, 0, false, false, false)
      }
    }
  }

  override def getFamilyName: String = ScalaCodeInsightBundle.message("family.name.insert.gap")

  override def getText: String = ScalaCodeInsightBundle.message("insert.gap.with.concatenation")
}

object InsertGapIntoStringIntention {

  private def getReplacement(editor: Editor, element: PsiElement): Option[(String, Int)] = {
    //it's cheaper to first just check the node element type before doing any other checks
    val replacementAndShift = element.getNode.getElementType match {
      case `tSTRING`           => Some("\" +  + \"", 4)
      case `tMULTILINE_STRING` => Some("\"\"\" +  + \"\"\"", 6)
      case _                   => None
    }

    def isCaretInsideStringContent: Boolean =
      element.getParent match {
        case stringLiteral: ScStringLiteral =>
          //don't show intention when caret is not inside string content
          stringLiteral.contentRange.containsOffset(editor.getCaretModel.getOffset)
        case _ => false
      }

    replacementAndShift.filter(_ => isCaretInsideStringContent)
  }
}
