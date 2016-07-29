package org.jetbrains.plugins.scala.annotator.quickfix.implicits

import javax.swing.Icon

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.Task.WithResult
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager}
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.ui.popup.{JBPopupFactory, PopupStep}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix
import org.jetbrains.plugins.scala.annotator.quickfix.implicits.SearchForImplicitClassAction.ImplicitSearchResult
import org.jetbrains.plugins.scala.extensions._

/**
  * Created by Svyatoslav Ilinskiy on 28.07.16.
  */
trait SearchImplicitPopup {
  def getText: String
  def searchingTitleText: String
  def element: PsiElement

  def showPopup(funs: Array[ImplicitSearchResult], editor: Editor): Unit = {
    val step: BaseListPopupStep[ImplicitSearchResult] = new BaseListPopupStep(getText, funs: _*) {
      override def getIconFor(value: ImplicitSearchResult): Icon = value.fun.getIcon(0)

      override def getTextFor(value: ImplicitSearchResult): String = {
        val fun = value.fun
        val cl = fun.containingClass
        s"${cl.name}.${fun.name} (${cl.qualifiedName})"
      }

      override def onChosen(value: ImplicitSearchResult, finalChoice: Boolean): PopupStep[_] = {
        val project = element.getProject
        val holder = ScalaImportTypeFix.getImportHolder(element, project)
        inWriteCommandAction(project, "Add import for implicit class") {
          holder.addImportForPsiNamedElement(value.elementToImport, null)
        }
        super.onChosen(value, finalChoice)
      }
    }
    val popup = JBPopupFactory.getInstance().createListPopup(step)
    popup.showInBestPositionFor(editor)
  }

  def searchWithProgress(fun: () => Seq[ImplicitSearchResult]): Option[Seq[ImplicitSearchResult]] = {
    val task = new WithResult[Seq[ImplicitSearchResult], RuntimeException](element.getProject, searchingTitleText, true) {
      override def compute(indicator: ProgressIndicator): Seq[ImplicitSearchResult] = {
        indicator.setIndeterminate(true)
        inReadAction {
          fun()
        }
      }
    }
    val progressManager = ProgressManager.getInstance()
    Option(progressManager.run(task))
  }
}
