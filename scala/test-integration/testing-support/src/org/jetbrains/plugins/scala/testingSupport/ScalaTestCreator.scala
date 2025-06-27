package org.jetbrains.plugins.scala.testingSupport

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.{PsiClass, PsiFile, PsiPackage}
import com.intellij.testIntegration.createTest.{CreateTestAction, CreateTestDialog}
import com.intellij.testIntegration.{JavaTestCreator, TestFramework}
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.{Nls, TestOnly}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.testingSupport.ScalaTestCreator._

class ScalaTestCreator extends JavaTestCreator {

  override def createTest(project: Project, editor: Editor, file: PsiFile): Unit = {
    try {
      val element = findElement(file, editor.getCaretModel.getOffset)
      if (CreateTestAction.isAvailableForElement(element)) {
        val action = new ScalaCreateTestAction()
        action.invoke(project, editor, element)
      }
    } catch {
      case e: IncorrectOperationException => Log.warn(e)
    }
  }
}

object ScalaTestCreator {

  private val Log = Logger.getInstance("org.jetbrains.plugins.scala.ScalaTestCreator")

  private def findElement(file: PsiFile, offset: Int) = {
    var element = file.findElementAt(offset)
    if (element == null && offset == file.getTextLength) element = file.findElementAt(offset - 1)
    element
  }

  private class ScalaCreateTestAction extends CreateTestAction {
    override protected def createTestDialog(
      project: Project,
      srcModule: Module,
      srcClass: PsiClass,
      srcPackage: PsiPackage
    ): CreateTestDialog =
      new ScalaCreateTestDialog(
        project = project,
        title = getText,
        targetClass = srcClass,
        targetPackage = srcPackage,
        targetModule = srcModule
      )
  }

  private class ScalaCreateTestDialog(
    project: Project,
    @Nls title: String,
    targetClass: PsiClass,
    targetPackage: PsiPackage,
    targetModule: Module,
  ) extends CreateTestDialog(project, title, targetClass, targetPackage, targetModule) {

    override def suggestTestClassName(targetClass: PsiClass): String = targetClass match {
      case obj: ScObject =>
        obj.getName.stripSuffix("$") + "Test"
      case _ =>
        super.suggestTestClassName(targetClass)
    }

    override def getSelectedTestFrameworkDescriptor: TestFramework =
      getMockTestData(project)(_.selectedTestFramework).getOrElse(super.getSelectedTestFrameworkDescriptor)

    override def getClassName: String =
      getMockTestData(project)(_.testClassName).getOrElse(super.getClassName)

    override def showAndGet(): Boolean = {
      if (ApplicationManager.getApplication.isUnitTestMode) {
        doOKAction()
        return true
      }

      super.showAndGet()
    }
  }

  @TestOnly
  case class MockTestDialogData(
    selectedTestFramework: TestFramework,
    testClassName: String
  )

  @TestOnly
  val MockTestDialogDataKey: Key[MockTestDialogData] =
    Key.create[MockTestDialogData]("scala.test.creator.mock.dialog.data")

  private def getMockTestData[T](project: Project)(f: MockTestDialogData => T): Option[T] =
    if (ApplicationManager.getApplication.isUnitTestMode)
      Option(project.getUserData(MockTestDialogDataKey)).map(f)
    else
      None
}