package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.awt.event.ActionEvent
import java.awt.{List => _, _}
import java.text.MessageFormat

import com.intellij.icons.AllIcons
import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.actionSystem.{ActionToolbarPosition, AnActionEvent}
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.{DialogWrapper, Messages}
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui._
import com.intellij.util.Processor
import com.intellij.util.ui.JBUI
import javax.swing._
import javax.swing.border.MatteBorder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.util.ImplicitUtil._

import scala.collection.JavaConverters._

private class ScalaImplicitMemberUsageSearcher
    extends QueryExecutorBase[PsiReference, ImplicitReferencesSearch.SearchParameters](true)
    with UsageToPsiElements {

  override def processQuery(
    parameters: ImplicitReferencesSearch.SearchParameters,
    consumer:   Processor[_ >: PsiReference]
  ): Unit = {
    val target  = parameters.element
    val project = target.getProject
    val service = ScalaCompilerReferenceService.getInstance(project)
    val usages  = service.usagesOf(target)
    val refs    = extractUsages(target, usages)
    refs.foreach(consumer.process)
  }

  private[this] def extractUsages(target: PsiElement, usages: Timestamped[Set[UsagesInFile]]): Seq[PsiReference] = {
    val project        = target.getProject
    val fileDocManager = FileDocumentManager.getInstance()
    val outdated       = Set.newBuilder[String]

    def extractReferences(usage: UsagesInFile): Seq[PsiReference] =
      (for {
        ElementsInContext(elements, file, doc) <- extractCandidatesFromUsage(project, usage)
      } yield {
        val isOutdated = fileDocManager.isDocumentUnsaved(doc) ||
          file.getVirtualFile.getTimeStamp > usages.timestamp

        val lineNumber = (e: PsiElement) => doc.getLineNumber(e.getTextOffset) + 1

        val refs       = elements.flatMap(target.refOrImplicitRefIn).toList
        val extraLines = usage.lines.diff(refs.map(r => lineNumber(r.getElement)))

        val unresolvedRefs = extraLines.flatMap { line =>
          val offset = doc.getLineStartOffset(line - 1)
          //@TODO: perhaps it makes sense to somehow show the number of unresolved implicits in this line
          if (!isOutdated) UnresolvedImplicitReference(target, file, offset).toOption
          else {
            outdated += file.getVirtualFile.getPresentableName
            None
          }
        }

        refs ++ unresolvedRefs
        //@TODO: invoked slow search for outdated files scope
      }).getOrElse(Seq.empty)

    val result        = usages.unwrap.flatMap(extractReferences)(collection.breakOut)
    val filesToNotify = outdated.result()

    if (filesToNotify.nonEmpty) {
      Notifications.Bus.notify(
        new Notification(
          ScalaBundle.message("find.usages.implicit.dialog.title"),
          "Implicit Usages Invalidated",
          s"Some usages in the following files were invalidated, due to external changes: ${filesToNotify.mkString(",")}.",
          NotificationType.WARNING
        )
      )
    }
    result
  }
}

object ScalaImplicitMemberUsageSearcher {
  private[findUsages] sealed trait BeforeImplicitSearchAction

  private[findUsages] case object CancelSearch   extends BeforeImplicitSearchAction
  private[findUsages] case object RebuildProject extends BeforeImplicitSearchAction
  private[findUsages] final case class BuildModules(modules: Seq[Module]) extends BeforeImplicitSearchAction

  private[findUsages] def assertSearchScopeIsSufficient(target: PsiNamedElement): Option[BeforeImplicitSearchAction] = {
    val project = target.getProject
    val service = ScalaCompilerReferenceService.getInstance(project)

    if (!service.isCompilerIndexReady) {
      inEventDispatchThread(showIndicesNotReadyDialog(project))
      Option(CancelSearch)
    } else if (service.isIndexingInProgress) {
      inEventDispatchThread(showIndexingInProgressDialog(project))
      Option(CancelSearch)
    } else {
      val (dirtyModules, upToDateModules) = dirtyModulesInDependencyChain(target)
      val validIndexExists                = upToDateCompilerIndexExists(project, ScalaCompilerIndices.version)

      if (dirtyModules.nonEmpty || !validIndexExists) {
        var action: Option[BeforeImplicitSearchAction] = None

        val dialogAction =
          () => action = Option(showRebuildSuggestionDialog(dirtyModules, upToDateModules, validIndexExists, target))

        inEventDispatchThread(dialogAction())
        action
      } else None
    }
  }

  private[this] def inEventDispatchThread[T](body: => T): Unit =
    if (SwingUtilities.isEventDispatchThread) body
    else invokeAndWait(body)

  private[this] def dirtyModulesInDependencyChain(element: PsiElement): (Seq[Module], Seq[Module]) = {
    val project      = element.getProject
    val file         = PsiTreeUtil.getContextOfType(element, classOf[PsiFile]).getVirtualFile
    val index        = ProjectFileIndex.getInstance(project)
    val modules      = index.getOrderEntriesForFile(file).asScala.map(_.getOwnerModule)
    val service      = ScalaCompilerReferenceService.getInstance(project)
    val dirtyModules = service.getDirtyScopeHolder.getAllDirtyModules
    modules.span(dirtyModules.contains)
  }

  private[this] def showIndexingInProgressDialog(project: Project): Unit = {
    val message = "Implicit usages search is unavailable during bytecode indexing."
    Messages.showInfoMessage(project, message, "Indexing In Progress")
  }

  private[this] def showIndicesNotReadyDialog(project: Project): Unit = {
    val message =
      """
        |Bytecode indices are currently undergoing an up-to-date check,
        |please wait until it's completed to search for implicit usages.
      """.stripMargin

    Messages.showInfoMessage(project, message, "Bytecode Indices Status")
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
      case OK_EXIT_CODE if !validIndexExists => RebuildProject
      case OK_EXIT_CODE                      => BuildModules(dirtyModules)
      case CANCEL_EXIT_CODE                  => CancelSearch
    }
  }

  private class ImplicitFindUsagesDialog(
    canBeParent:      Boolean,
    dirtyModules:     Seq[Module],
    upToDateModules:  Seq[Module],
    validIndexExists: Boolean,
    element:          PsiNamedElement
  ) extends DialogWrapper(element.getProject, canBeParent) {

    private[this] val buildDescription: String =
      s"""|<html>
          |<body>
          |Implicit usages search is only supported inside a compiled scope,<br>
          |but the use scope of member <code>{0}</code> contains dirty modules.<br>
          |<br>
          |You can:<br>
          |-&nbsp;<strong>Build</strong> some of the modules before proceeding, or<br>
          |-&nbsp;Search for usages in already up-to-date modules: <br>
          | &nbsp;<code>{1}</code>
          |<br>
          |<br>
          |Select modules to <strong>build</strong>:
          |</body>
          |</html>
          |""".stripMargin

    private[this] val rebuildDescription: String =
      s"""|<html>
          |<body>
          |Implicit usages search in only supported inside a compiled scope, <br>
          |via bytecode indices, but no valid indices exist.<br>
          |Please <strong>rebuild</strong> the project to initialise compiler indices.
          |</body>
          |</html>
          |""".stripMargin

    setTitle(ScalaBundle.message("find.usages.implicit.dialog.title"))
    setResizable(false)
    init()

    override def createActions(): Array[Action] = {
      val defaultActions = super.createActions()
      if (!validIndexExists)
        new DialogWrapperAction("Rebuild") {
          override def doAction(actionEvent: ActionEvent): Unit = doOKAction()
        } +: defaultActions.filterNot(getOKAction == _)
      else defaultActions
    }

    private def createDescriptionLabel: JComponent = {
      // @TODO: in case of context bound usages search element.name might not be a user-friendly identifier
      val upToDateModulesText = if (upToDateModules.isEmpty) "&lt;empty&gt;" else upToDateModules.mkString(", ")

      val message =
        if (validIndexExists) MessageFormat.format(buildDescription, element.name, upToDateModulesText)
        else rebuildDescription

      new JLabel(message)
    }

    private class DirtyModulesList() extends CheckBoxList[Module] {
      setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
      setItems(dirtyModules.asJava, _.getName)
      setItemsSelected(true)
      setBorder(JBUI.Borders.empty(3))

      def selectAllButton: AnActionButton =
        new AnActionButton("Select All", "", AllIcons.Actions.Selectall) {
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
      if (validIndexExists) {
        val panel = new JPanel(new BorderLayout)
        panel.add(createModulesList)
        panel
      } else null

    override def createNorthPanel(): JComponent = {
      val gbConstraints = new GridBagConstraints
      val panel         = new JPanel(new GridBagLayout)
      gbConstraints.insets = JBUI.insets(4, 0, 10, 8)
      panel.add(createDescriptionLabel, gbConstraints)
      panel
    }
  }
}
