package org.jetbrains.plugins.scala.codeInsight.template.impl

import com.intellij.codeInsight.template.impl.TemplatePreprocessor
import com.intellij.openapi.editor.Editor
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.extensions.PsiElementExt


/**
 * @author Roman.Shein
 *         Date: 19.10.2015
 */
class ScalaTemplatePreprocessor extends TemplatePreprocessor {
  override def preprocessTemplate(editor: Editor, file: PsiFile, caretOffset: Int, textToInsert: String, templateText: String): Unit = {
    Option(file.findElementAt(caretOffset)).map { caretElem =>
      var res = caretElem
      //use loop here so that we hop over error nodes
      do {
        res = res.getPrevSiblingNotWhitespace
      } while (res != null && res.getText.isEmpty)
      res
    }.foreach {
      case elem: LeafPsiElement if elem.getText == "def" && textToInsert.startsWith("def ") =>
        val document = editor.getDocument
        //first, make sure that the 'def' is on the same line (i.e. it is a redundant 'def' indeed and not a part of other unfinished code)
        if (document.getLineNumber(elem.getStartOffset) == document.getLineNumber(caretOffset)) {
          //get rid of extra 'def' when expanding 'main' template (any other templates with 'def' will get affected too)
          document.deleteString(elem.getStartOffset, caretOffset)
          editor.getCaretModel.moveToOffset(elem.getStartOffset)
        }
      case _ =>
    }
  }
}
