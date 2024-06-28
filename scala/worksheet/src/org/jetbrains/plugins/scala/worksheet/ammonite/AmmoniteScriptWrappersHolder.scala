package org.jetbrains.plugins.scala
package worksheet
package ammonite

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DaemonListener
import com.intellij.codeInsight.daemon.impl.{HighlightInfo, HighlightInfoType}
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.notification.{Notification, NotificationAction, NotificationType}
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.ex.{MarkupModelEx, RangeHighlighterEx}
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.fileEditor.{FileEditorManager, FileEditorManagerListener}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.startup.ProjectActivity
import org.jetbrains.plugins.scala.worksheet.WorksheetBundle
import org.jetbrains.plugins.scala.worksheet.utils.notifications.WorksheetNotificationsGroup

import java.util.function.BiFunction
import scala.collection.mutable

class AmmoniteScriptWrappersHolder(project: Project) {
  import AmmoniteScriptWrappersHolder._

  private val file2object = mutable.WeakHashMap.empty[ScalaFile, (ScObject, Long)]

  private val problemFiles = ContainerUtil.createConcurrentWeakMap[VirtualFile, Int]()
  private val disabledFiles = ContainerUtil.createConcurrentWeakMap[VirtualFile, DisabledState]()

  private var ignoreImportStandard = false

  private def createWrapper(from: ScalaFile) = {
    val obj = GotoOriginalHandlerUtil.createPsi((from: ScalaFile) => ScalaPsiElementFactory.createObjectWithContext(
      s"object ${AmmoniteScriptWrappersHolder.getWrapperName(from)} {\n${from.getText} }", from, from.getFirstChild
    ), from)
    GotoOriginalHandlerUtil.setGoToTarget(obj.getContainingFile, from)

    obj
  }

  def isIgnoreImport: Boolean = ignoreImportStandard

  def setIgnoreImports(): Unit = ignoreImportStandard = true

  def findWrapper(base: ScalaFile): Option[ScObject] = {
    if (!AmmoniteUtil.isAmmoniteFile(base)) None else {
      file2object.get(base) match {
        case Some((wrapper, timestamp)) if timestamp == base.getModificationStamp && wrapper.isValid => Option(wrapper)
        case _ =>
          val wrapper = createWrapper(base)
          val timestamp = base.getModificationStamp
          file2object.put(base, (wrapper, timestamp))
          Option(wrapper)
      }
    }
  }

  private def init(): Unit = {
    val connection = project.getMessageBus.connect(project.unloadAwareDisposable)
    connection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonListener {
      override def daemonFinished(): Unit = problemFiles.replaceAll(setMask(SET_DAEMON_MASK))
    })

    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener {
      override def fileClosed(source: FileEditorManager, file: VirtualFile): Unit = {
        decrementImpl(file, SET_OPEN_MASK)
      }

      override def fileOpened(source: FileEditorManager, file: VirtualFile): Unit = {
        getFile(file).foreach(ammoniteFileOpened)
      }
    })
  }


  def registerProblemIn(file: ScalaFile): Unit = {
    increment(file, SET_PROBLEM_MASK)
    decrement(file, SET_DAEMON_MASK)
  }

  def ammoniteFileOpened(file: ScalaFile): Unit = {
    increment(file, SET_OPEN_MASK)

    if (!isIgnoreImport) ImportAmmoniteDependenciesFix.suggestAddingAmmonite(file)
  }

  def onAmmoniteRun(vFile: VirtualFile): Unit = {
    if (getFile(vFile).isEmpty) return
    val state = disabledFiles get vFile
    if (state != AlwaysDisabled) disabledFiles.remove(vFile)
  }

  private def setFileState(vFile: VirtualFile, state: DisabledState): Unit = {
    disabledFiles.put(vFile, state)
  }

  private def increment(file: ScalaFile, mask: Int): Unit = {
    Option(file.getVirtualFile).foreach(incrementImpl(_, mask))
  }

  private def decrement(file: ScalaFile, mask: Int): Unit = {
    Option(file.getVirtualFile).foreach(decrementImpl(_, mask))
  }

  private def incrementImpl(vFile: VirtualFile, mask: Int): Unit = {
    problemFiles.merge(vFile, mask, (t: Int, u: Int) => t | u)

    tryFetching(vFile)
  }

  private def decrementImpl(vFile: VirtualFile, mask: Int): Unit = {
    problemFiles.computeIfPresent(vFile, removeMask(mask))
    problemFiles.remove(vFile, 0)
  }

  private def tryFetching(file: VirtualFile): Unit = {
    if (!disabledFiles.containsKey(file) && problemFiles.get(file) == READY_MASK) {
      decrementImpl(file, SET_DAEMON_MASK | SET_PROBLEM_MASK)
      showInfo(file)
    }
  }

  private def showInfo(vFile: VirtualFile): Unit = {
    setFileState(vFile, PerRunDisabled)

    object ImportAction extends NotificationAction(WorksheetBundle.message("ammonite.import.ivy.dependencies.action.import")) {
      override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
        notification.expire()
        getFile(vFile).foreach { ammFile =>
          val editorOpt = WorksheetUtils.getSelectedTextEditor(project, vFile)
          editorOpt.foreach { editor =>
            val acc = mutable.ArrayBuffer.empty[CreateImportedLibraryQuickFix]

            DocumentMarkupModel.forDocument(editor.getDocument, project, true).asInstanceOf[MarkupModelEx].processRangeHighlightersOverlappingWith(
              0, ammFile.getTextLength, (t: RangeHighlighterEx) => {
                t.getErrorStripeTooltip match {
                  case hInfo: HighlightInfo if hInfo.`type` == HighlightInfoType.WARNING =>
                    hInfo.findRegisteredQuickFix { case (descriptor, _) =>
                      QuickFixWrapper.unwrap(descriptor.getAction) match {
                        case ammoniteFix: CreateImportedLibraryQuickFix => acc.append(ammoniteFix)
                        case _ =>
                      }
                      null
                    }
                  case _ =>
                }
                true
              })

            CommandProcessor.getInstance().executeCommand(project, () => {
              acc.foreach {
                fix =>
                  fix.applyFix()
              }
            }, null, null)
          }
        }
      }
    }
    object IgnoreAction extends NotificationAction(WorksheetBundle.message("ammonite.import.ivy.dependencies.action.ignore")) {
      override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
        notification.expire()
        setFileState(vFile, AlwaysDisabled)
      }
    }

    WorksheetNotificationsGroup
      .createNotification(
        WorksheetBundle.message("notification.title.ammonite.imports.found"),
        WorksheetBundle.message("ammonite.import.ivy.dependencies.message", vFile.getName),
        NotificationType.WARNING
      )
      .addAction(ImportAction)
      .addAction(IgnoreAction)
      .notify(project)
  }

  private def getFile(vFile: VirtualFile) = {
    PsiManager.getInstance(project).findFile(vFile) match {
      case sf: ScalaFile if AmmoniteUtil.isAmmoniteFile(sf) => Option(sf)
      case _ => None
    }
  }
}

object AmmoniteScriptWrappersHolder {
  private val SET_PROBLEM_MASK = 1
  private val SET_OPEN_MASK = 2
  private val SET_DAEMON_MASK = 4

  private val READY_MASK = SET_PROBLEM_MASK | SET_OPEN_MASK | SET_DAEMON_MASK

  private abstract class DisabledState
  private case object AlwaysDisabled extends DisabledState
  private case object PerRunDisabled extends DisabledState

  private def setMask(mask: Int) = new BiFunction[VirtualFile, Int, Int] {
    override def apply(t: VirtualFile, u: Int): Int = u | mask
  }

  private def removeMask(mask: Int) = new BiFunction[VirtualFile, Int, Int] {
    override def apply(t: VirtualFile, u: Int): Int = u & ~mask
  }

  def getInstance(project: Project): AmmoniteScriptWrappersHolder =
    project.getService(classOf[AmmoniteScriptWrappersHolder])

  def getWrapperName(from: PsiFile): String = from.getName.stripSuffix("." + WorksheetFileType.getDefaultExtension)

  def getOffsetFix(from: PsiFile): Int = (getWrapperName(from) + "object  {\n").length

  final class Startup extends ProjectActivity {
    override def execute(project: Project): Unit = {
      getInstance(project).init()
    }
  }
}
