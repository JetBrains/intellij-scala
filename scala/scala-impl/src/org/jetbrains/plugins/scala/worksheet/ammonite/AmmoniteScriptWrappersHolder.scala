package org.jetbrains.plugins.scala
package worksheet
package ammonite

import java.util.function.BiFunction

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DaemonListener
import com.intellij.codeInsight.daemon.impl.{HighlightInfo, HighlightInfoType}
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.editor.ex.{MarkupModelEx, RangeHighlighterEx}
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.fileEditor.{FileEditorManager, FileEditorManagerListener}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.util.Processor
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.util.NotificationUtil
import org.jetbrains.plugins.scala.worksheet.actions.WorksheetFileHook

import scala.collection.mutable

/**
  * User: Dmitry.Naydanov
  * Date: 03.08.17.
  */
class AmmoniteScriptWrappersHolder(project: Project) extends ProjectComponent {
  import AmmoniteScriptWrappersHolder._
  
  private val file2object = mutable.WeakHashMap.empty[ScalaFile, (ScObject, Long)]
  
  private val problemFiles = ContainerUtil.createConcurrentWeakMap[VirtualFile, Int]()
  private val disabledFiles = ContainerUtil.createConcurrentWeakMap[VirtualFile, DisabledState]()
  
  private var ignoreImportStandard = false
  
  private def createWrapper(from: ScalaFile) = {
    val obj = GotoOriginalHandlerUtil.createPsi((from: ScalaFile) => ScalaPsiElementFactory.createObjectWithContext(
      s"object ${AmmoniteScriptWrappersHolder.getWrapperName(from)} {\n${from.getText} }", from, from.getFirstChild
    ), from)
    GotoOriginalHandlerUtil.storePsi(obj.getContainingFile, from)

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

  override def projectOpened(): Unit = {
    project.getMessageBus.connect(project).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonListener {
      override def daemonFinished(): Unit = problemFiles.replaceAll(setMask(SET_DAEMON_MASK))
    })

    project.getMessageBus.connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener {
      override def fileClosed(source: FileEditorManager, file: VirtualFile) {
        decrementImpl(file, SET_OPEN_MASK)
      }

      override def fileOpened(source: FileEditorManager, file: VirtualFile) {
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
  
  def onAmmoniteRun(vFile: VirtualFile) {
    if (getFile(vFile).isEmpty) return
    val state = disabledFiles get vFile
    if (state != AlwaysDisabled) disabledFiles.remove(vFile)
  }
  
  private def setFileState(vFile: VirtualFile, state: DisabledState) {
    disabledFiles.put(vFile, state)
  }
  
  private def increment(file: ScalaFile, mask: Int) {
    Option(file.getVirtualFile).foreach(incrementImpl(_, mask))
  }
  
  private def decrement(file: ScalaFile, mask: Int) {
    Option(file.getVirtualFile).foreach(decrementImpl(_, mask))
  }
  
  private def incrementImpl(vFile: VirtualFile, mask: Int) {
    problemFiles.merge(vFile, mask, new BiFunction[Int, Int, Int] {
      override def apply(t: Int, u: Int): Int = t | u
    })
    
    tryFetching(vFile)
  }
  
  private def decrementImpl(vFile: VirtualFile, mask: Int) {
    problemFiles.computeIfPresent(vFile, removeMask(mask))
    problemFiles.remove(vFile, 0)
  }
  
  private def tryFetching(file: VirtualFile) {
    if (!disabledFiles.containsKey(file) && problemFiles.get(file) == READY_MASK) {
      decrementImpl(file, SET_DAEMON_MASK | SET_PROBLEM_MASK)
      showInfo(file)
    }
  }
  
  private def showInfo(vFile: VirtualFile) {
    setFileState(vFile, PerRunDisabled)
    
    NotificationUtil.showMessage (
      project = project,
      title = "Ammonite imports found",
      message =
        s"""
          |<html>
          |   <body>
          |   <a href="ftp://run">Import</a> all $$ivy dependencies (present in local caches) declared in ${vFile.getName}? <a href="ftp://disable">Ignore</a>
          |   </body>
          |  </html>
        """.stripMargin, 
      handler = {
        case "run" => getFile(vFile) foreach {
          ammFile =>
            WorksheetFileHook.handleEditor(FileEditorManager.getInstance(project), vFile) {
              textEditor =>
                val acc = mutable.ArrayBuffer.empty[CreateImportedLibraryQuickFix]

                DocumentMarkupModel.forDocument(textEditor.getDocument, project, true).asInstanceOf[MarkupModelEx].processRangeHighlightersOverlappingWith(
                  0, ammFile.getTextLength, new Processor[RangeHighlighterEx] {
                    override def process(t: RangeHighlighterEx): Boolean = {
                      t.getErrorStripeTooltip match {
                        case hInfo: HighlightInfo if hInfo.`type` == HighlightInfoType.WEAK_WARNING =>
                          val it = hInfo.quickFixActionRanges.iterator()
                          while (it.hasNext) {
                            it.next().first.getAction match {
                              case wrapper: QuickFixWrapper =>
                                wrapper.getFix match {
                                  case ammoniteFix: CreateImportedLibraryQuickFix => acc.append(ammoniteFix)
                                  case _ =>
                                }
                              case _ =>
                            }
                          }
                        case _ =>
                      }
                      true
                    }
                  })

                CommandProcessor.getInstance().executeCommand(project, new Runnable {
                  override def run(): Unit = {
                    acc.foreach {
                      fix =>
                        fix.invoke(project, ammFile, ammFile, ammFile)
                    }
                  }
                }, null, null)
            }
        }
        case "disable" => setFileState(vFile, AlwaysDisabled)
        case _ =>
      }
    )
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
  
  private abstract class DisabledState {
    def disabled: Boolean = true
  }
  private case object AlwaysDisabled extends DisabledState
  private case object PerRunDisabled extends DisabledState
  
  private def setMask(mask: Int) = new BiFunction[VirtualFile, Int, Int] {
    override def apply(t: VirtualFile, u: Int): Int = u | mask
  } 
  
  private def removeMask(mask: Int) = new BiFunction[VirtualFile, Int, Int] {
    override def apply(t: VirtualFile, u: Int): Int = u & ~mask
  }
  
  def getInstance(project: Project): AmmoniteScriptWrappersHolder = project.getComponent(classOf[AmmoniteScriptWrappersHolder])

  def getWrapperName(from: PsiFile): String = from.getName.stripSuffix("." + WorksheetFileType.getDefaultExtension)
  
  def getOffsetFix(from: PsiFile): Int = (getWrapperName(from) + "object  {\n").length 
}