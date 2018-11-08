package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.awt.event.ActionEvent
import java.awt.{List => _, _}
import java.text.MessageFormat

import com.intellij.find.FindManager
import com.intellij.find.impl.FindManagerImpl
import com.intellij.icons.AllIcons
import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.actionSystem.{ActionToolbarPosition, AnActionEvent}
import com.intellij.openapi.application.{QueryExecutorBase, TransactionGuard}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.{DialogWrapper, Messages}
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.task.{ProjectTaskManager, ProjectTaskNotification}
import com.intellij.ui._
import com.intellij.util.Processor
import com.intellij.util.ui.JBUI
import javax.swing._
import javax.swing.border.MatteBorder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.factory.{ScalaFindUsagesHandler, ScalaFindUsagesHandlerFactory}
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.util.ImplicitUtil._
import org.jetbrains.sbt.shell.SbtShellCommunication
import org.jetbrains.sbt.shell.moduleBuildCommand

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
    val service = ScalaCompilerReferenceService(project)
    val usages  = service.usagesOf(target)
    val refs    = extractUsages(target, usages)
    refs.foreach(consumer.process)
  }

  private[this] def extractUsages(target: PsiElement, usages: Set[Timestamped[UsagesInFile]]): Seq[PsiReference] = {
    val project        = target.getProject
    val fileDocManager = FileDocumentManager.getInstance()
    val outdated       = Set.newBuilder[String]

    def extractReferences(usage: Timestamped[UsagesInFile]): Seq[PsiReference] =
      (for {
        ElementsInContext(elements, file, doc) <- extractCandidatesFromUsage(project, usage.unwrap)
      } yield {
        val isOutdated = fileDocManager.isDocumentUnsaved(doc) ||
          file.getVirtualFile.getTimeStamp > usage.timestamp

        val lineNumber = (e: PsiElement) => doc.getLineNumber(e.getTextOffset) + 1

        val refs       = elements.flatMap(target.refOrImplicitRefIn).toList
        val extraLines = usage.unwrap.lines.diff(refs.map(r => lineNumber(r.getElement)))

        val unresolvedRefs = extraLines.flatMap { line =>
          val offset = doc.getLineStartOffset(line - 1)
          if (!isOutdated) UnresolvedImplicitReference(target, file, offset).toOption
          else {
            outdated += file.getVirtualFile.getPresentableName
            None
          }
        }

        refs ++ unresolvedRefs
      }).getOrElse(Seq.empty)

    val result        = usages.flatMap(extractReferences)(collection.breakOut)
    val filesToNotify = outdated.result()

    if (filesToNotify.nonEmpty) {
      Notifications.Bus.notify(
        new Notification(
          ScalaBundle.message("find.usages.implicit.dialog.title"),
          "Implicit Usages Invalidated",
          s"Some usages in the following files may have been invalidated, due to external changes: ${filesToNotify.mkString(",")}.",
          NotificationType.WARNING
        )
      )
    }
    result
  }
}

object ScalaImplicitMemberUsageSearcher {
  private[findUsages] sealed trait BeforeImplicitSearchAction {
    def runAction(): Boolean
  }

  private[findUsages] case object CancelSearch extends BeforeImplicitSearchAction {
    override def runAction(): Boolean = false
  }

  private[findUsages] final case class RebuildProject(project: Project) extends BeforeImplicitSearchAction {
    override def runAction(): Boolean = {
      ScalaCompilerReferenceService(project).inTransaction {
        case (CompilerMode.JPS, _) => ProjectTaskManager.getInstance(project).rebuildAllModules()
        case (CompilerMode.SBT, _) =>
          // there is no need to do a full rebuild with sbt project as
          // we can simply fetch info about ALL classes instead of just
          // the ones built incrementally via incrementalityType setting
          def setIncrementalityType(incremental: Boolean): String = {
            val incType = if (incremental) "Incremental" else "NonIncremental"
            s"set incrementalityType in Global := _root_.org.jetbrains.sbt.indices.IntellijIndexer.IncrementalityType.$incType"
          }

          val shell         = SbtShellCommunication.forProject(project)
          val modules       = project.sourceModules
          val buildCommands = modules.flatMap(moduleBuildCommand)

          val buildCommand  =
            if (buildCommands.isEmpty) ""
            else                       buildCommands.mkString("all ", " ", "")

          val command = s"; ${setIncrementalityType(incremental = false)} ; $buildCommand ; ${setIncrementalityType(incremental = true)}"
          shell.command(command)
      }

      false
    }
  }

  private[findUsages] final case class BuildModules(
    target:  PsiElement,
    project: Project,
    modules: Seq[Module]
  ) extends BeforeImplicitSearchAction {
    override def runAction(): Boolean = {
      val manager    = ProjectTaskManager.getInstance(project)
      val connection = project.getMessageBus.connect(project)

      //FIXME: sbt compilation consists of multiple
      connection.subscribe(CompilerReferenceServiceStatusListener.topic, new CompilerReferenceServiceStatusListener {
        override def onIndexingFinished(): Unit = {
          val findManager = FindManager.getInstance(project).asInstanceOf[FindManagerImpl]
          val handler     = new ScalaFindUsagesHandler(target, ScalaFindUsagesHandlerFactory.getInstance(project))

          val runnable: Runnable = () =>
            findManager.getFindUsagesManager.findUsages(
              handler.getPrimaryElements,
              handler.getSecondaryElements,
              handler,
              handler.getFindUsagesOptions(),
              false
            )

          TransactionGuard.getInstance().submitTransactionAndWait(runnable)
        }
        connection.disconnect()
      })

      val notification: ProjectTaskNotification =
        result => if (result.isAborted || result.getErrors != 0) connection.disconnect()

      manager.build(modules.toArray, notification)
      false
    }
  }

  private[findUsages] def assertSearchScopeIsSufficient(target: PsiNamedElement): Option[BeforeImplicitSearchAction] = {
    val project = target.getProject
    val service = ScalaCompilerReferenceService(project)

    if (service.isIndexingInProgress) {
      inEventDispatchThread(showIndexingInProgressDialog(project))
      Option(CancelSearch)
    } else {
      val (dirtyModules, upToDateModules) = dirtyModulesInDependencyChain(target)
      val validIndexExists                = upToDateCompilerIndexExists(project, ScalaCompilerIndices.version)

      if (dirtyModules.nonEmpty || !validIndexExists) {
        var action: Option[BeforeImplicitSearchAction] = None

        val dialogAction =
          () =>
            action = Option(
              showRebuildSuggestionDialog(project, dirtyModules, upToDateModules, validIndexExists, target)
          )

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
    val service      = ScalaCompilerReferenceService(project)
    val dirtyModules = service.getDirtyScopeHolder.getAllDirtyModules
    modules.partition(dirtyModules.contains)
  }

  private[this] def showIndexingInProgressDialog(project: Project): Unit = {
    val message = "Implicit usages search is unavailable during bytecode indexing."
    Messages.showInfoMessage(project, message, "Indexing In Progress")
  }

  private def showRebuildSuggestionDialog(
    project:          Project,
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
      case OK_EXIT_CODE if !validIndexExists => RebuildProject(project)
      case OK_EXIT_CODE                      => BuildModules(element, project, dirtyModules)
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
        else                  rebuildDescription

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
