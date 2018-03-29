package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.awt._
import java.text.MessageFormat

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi._
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiUtilCore
import com.intellij.ui.{CheckBoxList, JBColor, ScrollPaneFactory}
import com.intellij.util.ui.JBUI
import com.intellij.util.{Processor, QueryExecutor}
import javax.swing._
import javax.swing.border.MatteBorder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.ImplicitUsageSearcher.ImplicitFindUsagesDialog
import org.jetbrains.plugins.scala.util.ImplicitUtil._

import scala.collection.JavaConverters._

class ImplicitUsageSearcher extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {
  override def execute(
    parameters: ReferencesSearch.SearchParameters,
    consumer: Processor[PsiReference]
  ): Boolean = parameters.getElementToSearch.implicitTarget.forall { target =>
    val (dirtyModules, upToDateModules) = dirtyModulesInDependencyChain(target)

    val shouldStop = if (dirtyModules.nonEmpty) {
      var exitCode     = DialogWrapper.OK_EXIT_CODE
      val dialogAction = () => showRebuildSuggestionDialog(dirtyModules, upToDateModules, target)(r => exitCode = r)

      if (SwingUtilities.isEventDispatchThread) dialogAction()
      else invokeAndWait(dialogAction())
      exitCode == DialogWrapper.CANCEL_EXIT_CODE
    } else false

    if (!shouldStop) {
      val project = inReadAction(target.getProject)
      val service = ScalaCompilerReferenceService.getInstance(project)
      val usages  = service.implicitUsages(target)
      processUsages(target, usages, consumer)
      true
    } else false
  }

  private def processUsages(
    target: PsiElement,
    usages: Set[LinesWithUsagesInFile],
    consumer: Processor[PsiReference]
  ): Unit = inReadAction {
    val project = target.getProject
    usages.foreach {
      case LinesWithUsagesInFile(file, lines) =>
        for {
          psiFile   <- PsiManager.getInstance(project).findFile(file).toOption
          document  <- PsiDocumentManager.getInstance(project).getDocument(psiFile).toOption
          predicate = (e: PsiElement) => lines.contains(document.getLineNumber(e.getTextOffset))
        } yield psiFile.depthFirst(predicate).flatMap(target.refOrImplicitRefIn).foreach(consumer.process)
    }
  }

  private def showRebuildSuggestionDialog[T](
    dirtyModules: Seq[Module],
    upToDateModules: Seq[Module],
    element: PsiNamedElement
  )(callback: Int => T): Unit = {
    val dialog = new ImplicitFindUsagesDialog(false, dirtyModules, upToDateModules, element)
    dialog.show()
    val exitCode = dialog.getExitCode
    callback(exitCode)
  }

  private def dirtyModulesInDependencyChain(element: PsiElement): (Seq[Module], Seq[Module]) = inReadAction {
    val project      = element.getProject
    val file         = PsiUtilCore.getVirtualFile(element)
    val index        = ProjectFileIndex.getInstance(project)
    val modules      = index.getOrderEntriesForFile(file).asScala.map(_.getOwnerModule)
    val service      = ScalaCompilerReferenceService.getInstance(project)
    val dirtyModules = service.getDirtyScopeHolder.getAllDirtyModules
    modules.span(dirtyModules.contains)
  }
}

object ImplicitUsageSearcher {
  private class ImplicitFindUsagesDialog(
    canBeParent: Boolean,
    dirtyModules: Seq[Module],
    upToDateModules: Seq[Module],
    element: PsiNamedElement
  ) extends DialogWrapper(inReadAction(element.getProject), canBeParent) {
    setTitle(ScalaBundle.message("find.usages.implicit.dialog.title"))
    init()

    override def createActions(): Array[Action] = super.createActions()

    private[this] val description: String =
      """
        |<html>
        |<body>
        |Implicit usages search is only supported inside a compiled scope,
        |but the use scope of member <code>{0}</code> contains dirty modules.
        |You can:<br>
        |-&nbsp;Rebuild some modules before proceeding, or<br>
        |-&nbsp;Only search for usages usage already up-to-date modules: <br>
        | &nbsp;<code>{1}</code>
        |<br>
        |Check modules you want to rebuild:
        |</body>
        |</html>
        |"""".stripMargin

    private def createDescriptionLabel: JComponent = {
      val message = MessageFormat.format(description, element.name, upToDateModules.mkString(", "))
      new JLabel(message)
    }

    private def createModulesList: JComponent = {
      val dirtyModulesList = new CheckBoxList[Module]()

      applyTo(dirtyModulesList)(
        _.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION),
        _.setItems(dirtyModules.asJava, _.getName),
        _.setBorder(JBUI.Borders.empty(5))
      )

      val modulesScrollPane = ScrollPaneFactory.createScrollPane(
        dirtyModulesList,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
      )

      modulesScrollPane.setBorder(new MatteBorder(0, 0, 1, 0, JBColor.border()))
      modulesScrollPane.setMaximumSize(new Dimension(-1, 300))
      modulesScrollPane
    }

    override def createCenterPanel(): JComponent =
      applyTo(new JPanel(new BorderLayout()))(
        _.add(createModulesList)
      )

    override def createNorthPanel(): JComponent = {
      val gbConstraints = new GridBagConstraints
      val panel         = new JPanel(new GridBagLayout)
      gbConstraints.insets = JBUI.insets(4, 0, 10, 8)
      panel.add(createDescriptionLabel, gbConstraints)
      panel
    }
  }
}
