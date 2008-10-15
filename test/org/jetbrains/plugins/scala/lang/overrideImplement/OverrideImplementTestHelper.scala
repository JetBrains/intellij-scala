package org.jetbrains.plugins.scala.lang.overrideImplement

import _root_.com.intellij.testFramework.PsiTestCase
import _root_.junit.framework.Test
import _root_.org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import _root_.org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.{PsiElement, PsiFile}
import java.io.File
import psi.api.ScalaFile
import psi.api.toplevel.typedef.ScTypeDefinition
import psi.ScalaPsiUtil
import util.ScalaTestUtils

/**
 * User: Alexander Podkhalyuzin
 * Date: 03.10.2008
 */

object OverrideImplementTestHelper {
  private val CARET_MARKER = "<caret>"
  private def removeMarker(text: String): String = {
    val index = text.indexOf(CARET_MARKER);
    return text.substring(0, index) + text.substring(index + CARET_MARKER.length());
  }

  /*
   *  File must be like:
   *  implement (or override) + " " +  methodName
   *  <typeDefinition>
   *  Use <caret> to specify caret position.
   */
  def transform(myProject: Project, file: PsiFile, offset: Int, isImplement: Boolean, methodName: String): String = {
    var element: PsiElement = file.findElementAt(offset)
    while (element != null && !element.isInstanceOf[ScTypeDefinition]) element = element.getParent
    val clazz = element match {
      case null => assert(false, "caret must be in type definition"); return "error"
      case x: ScTypeDefinition => x
    }
    val method = ScalaOIUtil.getMethod(clazz, methodName, isImplement)
    val anchor = ScalaOIUtil.getAnchor(offset, clazz)
    val runnable = new Runnable() {
      def run() {
        ScalaPsiUtil.adjustTypes(clazz.addMember(method, anchor))
        val myTextRange = file.getTextRange()
        CodeStyleManager.getInstance(myProject).reformatText(file, myTextRange.getStartOffset(), myTextRange.getEndOffset())
      }
    };
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      def run() {
        CommandProcessor.getInstance().executeCommand(myProject, runnable, "test", null);
      }
    });
    System.out.println("------------------------ " + file.getName + " ------------------------");
    System.out.println(file.getText());
    System.out.println("");
    return file.getText();
  }
}