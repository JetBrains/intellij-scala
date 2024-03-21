package org.jetbrains.plugins.scala.lang.refactoring.inline

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.{GlobalSearchScope, PsiSearchHelper}
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.ui.RefactoringDialog
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.NonNullObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

import java.awt.BorderLayout
import javax.swing.{JComponent, JLabel, JPanel, SwingConstants, UIManager}
import scala.util.chaining.scalaUtilChainingOps

abstract class ScalaInlineDialog(element: ScNamedElement, @NlsContexts.DialogTitle title: String, helpId: String)
                                (implicit project: Project) extends RefactoringDialog(project, true) {
  locally {
    init()
    setTitle(title)
  }

  protected def createProcessor(): BaseRefactoringProcessor

  @Nls protected def inlineQuestion: String

  override val getHelpId: String = helpId

  private lazy val occurrenceNumber: Int = {
    val searchHelper = PsiSearchHelper.getInstance(project)
    val scope = GlobalSearchScope.projectScope(project)
    val searchCost = searchHelper.isCheapEnoughToSearch(element.name, scope, null)
    val isCheapToSearch = searchCost != PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES

    if (isCheapToSearch) ReferencesSearch.search(element, scope).findAll().size()
    else -1
  }

  override def doAction(): Unit = invokeRefactoring(createProcessor())

  override def createCenterPanel(): JComponent =
    new JPanel(new BorderLayout()).tap { panel =>
      val message = inlineQuestion.pipeIf(occurrenceNumber > 0) { question =>
        val occurrences = ScalaBundle.message("inline.occurrences.label", occurrenceNumber)
        s"$question $occurrences"
      }

      val label = new JLabel(message, UIManager.getIcon("OptionPane.questionIcon"), SwingConstants.LEFT)
      panel.add(label)
    }
}
