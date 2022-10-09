package org.jetbrains.plugins.scala
package testingSupport

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiClass, PsiFile, PsiPackage}
import com.intellij.testIntegration.JavaTestCreator
import com.intellij.testIntegration.createTest.{CreateTestAction, CreateTestDialog}
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

class ScalaTestCreator extends JavaTestCreator {
  override def createTest(project: Project, editor: Editor, file: PsiFile): Unit = {
    import ScalaTestCreator._
    try {
      val action = new CreateTestAction {
        override protected def createTestDialog(project: Project, srcModule: Module, srcClass: PsiClass,
                                                srcPackage: PsiPackage): CreateTestDialog =
          new CreateTestDialog(project, getText, srcClass, srcPackage, srcModule) {
            override def suggestTestClassName(targetClass: PsiClass): String = targetClass match {
              case obj: ScObject => obj.getName.stripSuffix("$") + "Test"
              case _ => super.suggestTestClassName(targetClass)
            }
          }
      }
      val element = findElement(file, editor.getCaretModel.getOffset)
      if (CreateTestAction.isAvailableForElement(element)) action.invoke(project, editor, element)
    } catch {
      case e: IncorrectOperationException => LOG.warn(e)
    }
  }
}

object ScalaTestCreator {
  private val LOG = Logger.getInstance("org.jetbrains.plugins.scala.ScalaTestCreator")
  private def findElement(file: PsiFile, offset: Int) = {
    var element = file.findElementAt(offset)
    if (element == null && offset == file.getTextLength) element = file.findElementAt(offset - 1)
    element
  }
}