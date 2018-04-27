package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.awt.{List => _, _}
import java.text.MessageFormat

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{ActionToolbarPosition, AnActionEvent}
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.{DialogWrapper, Messages}
import com.intellij.psi._
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiUtilCore
import com.intellij.ui._
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
    consumer:   Processor[PsiReference]
  ): Unit =
    parameters.getElementToSearch match {
      case implicitSearchTarget(target) =>
        val project = target.getProject
        val service = ScalaCompilerReferenceService.getInstance(project)
        val usages  = service.implicitUsages(target)
        val refs    = extractUsages(target, usages)
        refs.foreach(consumer.process)
      case _ =>
    }

  private def extractUsages(target: PsiElement, usages: Set[LinesWithUsagesInFile]): Set[PsiReference] = {
    val project = target.getProject

    def extractElements(usage: LinesWithUsagesInFile): Seq[PsiElement] =
      (for {
        psiFile   <- PsiManager.getInstance(project).findFile(usage.file).toOption
        document  <- PsiDocumentManager.getInstance(project).getDocument(psiFile).toOption
        predicate = (e: PsiElement) => usage.lines.contains(document.getLineNumber(e.getTextOffset) + 1)
      } yield psiFile.depthFirst().filter(predicate).toList).getOrElse(List.empty)

    usages.flatMap(extractElements).flatMap(target.refOrImplicitRefIn)
  }
}

object ImplicitUsageSearcher {
  private[findUsages] sealed trait BeforeImplicitSearchAction

  private[findUsages] case object CancelSearch extends BeforeImplicitSearchAction

  private[findUsages] final case class BuildModules(modules: Seq[Module], clean: Boolean = false)
      extends BeforeImplicitSearchAction

  private[findUsages] def assertSearchScopeIsSufficient(target: PsiNamedElement): Option[BeforeImplicitSearchAction] = {
    val project = target.getProject
    val service = ScalaCompilerReferenceService.getInstance(project)

    if (!service.isCompilerIndexReady) {
      inEventDispatchThread(showIndicesNotReadyDialog(project))
      Option(CancelSearch)
    } else {
      val (dirtyModules, upToDateModules) = dirtyModulesInDependencyChain(target)

      if (dirtyModules.nonEmpty) {
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
  
  private def showIndicesNotReadyDialog(project: Project): Unit = {
    val message =
      """
        |Compiler indices are currently undergoing an up-to-date check, 
        |please wait until it's completed to search for implicit usages.
      """.stripMargin
    
    Messages.showInfoMessage(project, message, "Compiler Indices Status")
  }

  private def showRebuildSuggestionDialog[T](
    dirtyModules:     Seq[Module],
    upToDateModules:  Seq[Module],
    validIndexExists: Boolean,
    element:          PsiNamedElement
  ): BeforeImplicitSearchAction = {
    import DialogWrapper.{CANCEL_EXIT_CODE, OK_EXIT_CODE}

    val dialog = new ImplicitFindUsagesDialog(
      false,
      dirtyModules,
      upToDateModules,
      validIndexExists,
      element
    )
    dialog.show()

    dialog.getExitCode match {
      case OK_EXIT_CODE     => BuildModules(dirtyModules, !validIndexExists)
      case CANCEL_EXIT_CODE => CancelSearch
    }
  }

  private class ImplicitFindUsagesDialog(
    canBeParent:      Boolean,
    dirtyModules:     Seq[Module],
    upToDateModules:  Seq[Module],
    validIndexExists: Boolean,
    element:          PsiNamedElement
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
    setResizable(false)
    init()

    override def createActions(): Array[Action] = super.createActions()

    private def createDescriptionLabel: JComponent = {
      // @TODO: in case of context bound usages search element.name might not be a user-friendly identifier
      val upToDateModulesText = if (upToDateModules.isEmpty) "&lt;empty&gt;" else upToDateModules.mkString(", ")
      val message             = MessageFormat.format(description, element.name, upToDateModulesText)
      new JLabel(message)
    }

    private class DirtyModulesList() extends CheckBoxList[Module] {
      setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
      setItems(dirtyModules.asJava, _.getName)
      setBorder(JBUI.Borders.empty(3))

      def selectAllButton: AnActionButton = new AnActionButton("Select All", "", AllIcons.Actions.Selectall) {
        override def actionPerformed(e: AnActionEvent): Unit =
          setItemsSelected(true)
      }

      def unselectAllButton: AnActionButton =
        new AnActionButton("Unselect All", "", AllIcons.Actions.Unselectall) {
          override def actionPerformed(e: AnActionEvent): Unit =
            setItemsSelected(false)
        }

      def setItemsSelected(selected: Boolean): Unit = {
        (0 until getItemsCount).foreach(idx => setItemSelected(getItemAt(idx), selected))
        repaint()
      }
    }

    private def createModulesList: JComponent = {
      val dirtyModulesList = new DirtyModulesList()

      val panel = ToolbarDecorator
        .createDecorator(dirtyModulesList)
        .disableRemoveAction()
        .disableAddAction()
        .addExtraAction(dirtyModulesList.selectAllButton)
        .addExtraAction(dirtyModulesList.unselectAllButton)
        .setToolbarPosition(ActionToolbarPosition.BOTTOM)
        .setToolbarBorder(JBUI.Borders.empty())
        .createPanel()

      panel.setBorder(new MatteBorder(0, 0, 1, 0, JBColor.border()))
      panel.setMaximumSize(JBUI.size(-1, 300))
      panel
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
