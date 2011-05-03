package org.jetbrains.plugins.scala
package testingSupport

import com.intellij.openapi.project.Project
import com.intellij.testIntegration.createTest.{CreateTestDialog, TestGenerator}
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.psi.{JavaDirectoryService, PsiClass, PsiElement}
import com.intellij.openapi.editor.Editor
import com.intellij.util.IncorrectOperationException
import com.intellij.openapi.util.Computable
import actions.NewScalaTypeDefinitionAction
import com.intellij.openapi.ui.Messages
import com.intellij.codeInsight.{CodeInsightBundle, CodeInsightUtil}

class ScalaTestGenerator extends TestGenerator {
  def generateTest(project: Project, d: CreateTestDialog): PsiElement = {
    return PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(new Computable[PsiElement] {
      def compute: PsiElement = {
        return ApplicationManager.getApplication.runWriteAction(new Computable[PsiElement] {
          def compute: PsiElement = {
            try {
              IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace()
              val SCALA_EXTENSIOIN = "." + ScalaFileType.DEFAULT_EXTENSION
              val file = NewScalaTypeDefinitionAction.createFromTemplate(d.getTargetDirectory, d.getClassName, d.getClassName + SCALA_EXTENSIOIN, "Scala Class")
              // TODO add superclass d.getSuperClassName
              // TODO position cursor at class
              var editor: Editor = CodeInsightUtil.positionCursor(project, file, file.getFirstChild)
              // TODO add test methods
//              addTestMethods(editor, targetClass, d.getSelectedTestFrameworkDescriptor, d.getSelectedMethods, d.shouldGeneratedBefore, d.shouldGeneratedAfter)
              return file
            }
            catch {
              case e: IncorrectOperationException => {
                showErrorLater(project, d.getClassName)
                return null
              }
            }
          }
        })
      }
    })
  }

  override def toString: String = ScalaFileType.SCALA_LANGUAGE.getDisplayName

  private def showErrorLater(project: Project, targetClassName: String) {
      ApplicationManager.getApplication.invokeLater(new Runnable {
        def run() {
          Messages.showErrorDialog(project, CodeInsightBundle.message("intention.error.cannot.create.class.message", targetClassName), CodeInsightBundle.message("intention.error.cannot.create.class.title"))
        }
      })
    }
  }