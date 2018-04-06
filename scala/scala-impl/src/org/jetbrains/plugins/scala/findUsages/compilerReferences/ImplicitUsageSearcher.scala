package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.awt._
import java.text.MessageFormat

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi._
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiUtilCore
import com.intellij.ui.{CheckBoxList, JBColor, ScrollPaneFactory}
import com.intellij.util.Processor
import com.intellij.util.ui.JBUI
import javax.swing._
import javax.swing.border.MatteBorder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.util.ImplicitUtil._

import scala.collection.JavaConverters._

class ImplicitUsageSearcher extends QueryExecutorBase[PsiReference, ReferencesSearch.SearchParameters](true) {
  override def processQuery(
    parameters: ReferencesSearch.SearchParameters,
    consumer: Processor[PsiReference]
  ): Unit =
    parameters.getElementToSearch match {
      case implicitSearchTarget(target) =>
        val project = target.getProject
        val service = ScalaCompilerReferenceService.getInstance(project)
        val usages  = service.implicitUsages(target)
        processUsages(target, usages, consumer)
      case _ =>
    }

  private def processUsages(
    target: PsiElement,
    usages: Set[LinesWithUsagesInFile],
    consumer: Processor[PsiReference]
  ): Unit = {
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
}

object ImplicitUsageSearcher {
  private[findUsages] sealed trait BeforeImplicitSearchAction

  private[findUsages] case object CancelSearch extends BeforeImplicitSearchAction

  private[findUsages] final case class BuildModules(modules: Seq[Module], clean: Boolean = false)
      extends BeforeImplicitSearchAction

  private[findUsages] def assertSearchScopeIsSufficient(target: PsiNamedElement): Option[BeforeImplicitSearchAction] = {
    val (dirtyModules, upToDateModules) = dirtyModulesInDependencyChain(target)

    if (dirtyModules.nonEmpty) {
      val project                                    = target.getProject
      var action: Option[BeforeImplicitSearchAction] = None

      val dialogAction =
        () => {
          val validIndexExists = upToDateCompilerIndexExists(project)
          action = Option(showRebuildSuggestionDialog(dirtyModules, upToDateModules, validIndexExists, target))
        }

      inEventDispatchThread(dialogAction())
      action
    } else None
  }
  
  private def inEventDispatchThread[T](body: => T): Unit =
    if (SwingUtilities.isEventDispatchThread) body
    else invokeAndWait(body)

  private def dirtyModulesInDependencyChain(element: PsiElement): (Seq[Module], Seq[Module]) = {
    inEventDispatchThread(FileDocumentManager.getInstance().saveAllDocuments())
    val project      = element.getProject
    val file         = PsiUtilCore.getVirtualFile(element)
    val index        = ProjectFileIndex.getInstance(project)
    val modules      = index.getOrderEntriesForFile(file).asScala.map(_.getOwnerModule)
    val service      = ScalaCompilerReferenceService.getInstance(project)
    val dirtyModules = service.getDirtyScopeHolder.getAllDirtyModules
    modules.span(dirtyModules.contains)
  }

  private def showRebuildSuggestionDialog[T](
    dirtyModules: Seq[Module],
    upToDateModules: Seq[Module],
    validIndexExists: Boolean,
    element: PsiNamedElement
  ): BeforeImplicitSearchAction = {
    import DialogWrapper.{CANCEL_EXIT_CODE, OK_EXIT_CODE}

    val dialog = new ImplicitFindUsagesDialog(false, dirtyModules, upToDateModules, validIndexExists, element)
    dialog.show()

    dialog.getExitCode match {
      case OK_EXIT_CODE     => BuildModules(dirtyModules)
      case CANCEL_EXIT_CODE => CancelSearch
    }
  }

  private class ImplicitFindUsagesDialog(
    canBeParent: Boolean,
    dirtyModules: Seq[Module],
    upToDateModules: Seq[Module],
    validIndexExists: Boolean,
    element: PsiNamedElement
  ) extends DialogWrapper(element.getProject, canBeParent) {
    private[this] val moduleAction: String = if (validIndexExists) "build" else "rebuild"
    
    private[this] val description: String =
      s"""|<html>
          |<body>
          |Implicit usages search is only supported inside a compiled scope,<br>
          |but the use scope of member <code>{0}</code> contains dirty modules.<br>
          |<br>
          |You can:<br>
          |-&nbsp;<strong>${moduleAction.capitalize}</strong> some of the modules before proceeding, or<br>
          |-&nbsp;Search for usages in already up-to-date modules: <br>
          | &nbsp;<code>{1}</code>
          |<br>
          |<br>
          |Select modules to <strong>$moduleAction</strong>:
          |</body>
          |</html>
          |""".stripMargin

    setTitle(ScalaBundle.message("find.usages.implicit.dialog.title"))
    init()

    override def createActions(): Array[Action] = super.createActions()

    private def createDescriptionLabel: JComponent = {
      // @TODO: in case of context bound usages search element.name might not be a user-friendly identifier
      val upToDateModulesText = if (upToDateModules.isEmpty) "&lt;empty&gt;" else upToDateModules.mkString(", ")
      val message             = MessageFormat.format(description, element.name, upToDateModulesText)
      new JLabel(message)
    }

    private def createModulesList: JComponent = {
      val dirtyModulesList = new CheckBoxList[Module]()

      applyTo(dirtyModulesList)(
        _.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION),
        _.setItems(dirtyModules.asJava, _.getName),
        _.setBorder(JBUI.Borders.empty(3))
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
