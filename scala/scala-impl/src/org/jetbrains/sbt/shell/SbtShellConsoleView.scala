package org.jetbrains.sbt
package shell

import java.beans.{PropertyChangeEvent, PropertyChangeListener}
import com.intellij.execution.actions.ClearConsoleAction
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.console.LanguageConsoleImpl
import com.intellij.execution.filters.UrlFilter.UrlFilterProvider
import com.intellij.execution.filters._
import com.intellij.openapi.actionSystem.{ActionGroup, AnAction, DefaultActionGroup}
import com.intellij.openapi.editor.actions.ToggleUseSoftWrapsToolbarAction
import com.intellij.openapi.editor.event.{EditorMouseEvent, EditorMouseListener}
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.{Editor, EditorFactory}
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.sbt.shell.action._

import java.util.Collections
import scala.collection.mutable

/**
  * Created by jast on 2017-05-17.
  */
final class SbtShellConsoleView private(project: Project, debugConnection: Option[RemoteConnection])
  extends LanguageConsoleImpl(project, SbtShellLanguage.getID, SbtShellLanguage) {

  def createActionGroup(): ActionGroup = {
    val group = new DefaultActionGroup()

    // hackery because we can't construct those actions directly
    val defaultActions = super.createConsoleActions()
    val toggleSoftWrapsAction = defaultActions.find(_.isInstanceOf[ToggleUseSoftWrapsToolbarAction])
      .getOrElse(throw new RuntimeException("action of type `ToggleUseSoftWrapsToolbarAction` couldn't be found"))
    val clearAllAction = defaultActions.find(_.isInstanceOf[ClearConsoleAction])
      .getOrElse(throw new RuntimeException("action of type `ClearConsoleAction` couldn't be found"))

    val startAction = new StartAction(project)
    val stopAction = new StopAction(project)
    val debugShellAction = new DebugShellAction(project, debugConnection)
    val scrollToTheEndToolbarAction = new SbtShellScrollToTheEndToolbarAction(getEditor)
    val eofAction = new EOFAction(project)
    val sigIntAction = new SigIntAction(project, this)
    val copyFromHistoryViewerAction = new CopyFromHistoryViewerAction(this)

    val allActions: Array[AnAction] = Array(
      startAction,
      stopAction,
      debugShellAction,
      scrollToTheEndToolbarAction,
      toggleSoftWrapsAction,
      clearAllAction,
      eofAction,
      sigIntAction,
      copyFromHistoryViewerAction
    )

    allActions.foreach { act =>
      act.registerCustomShortcutSet(act.getShortcutSet, this)
    }

    group.addAll(startAction, stopAction, debugShellAction)
    group.addSeparator()
    group.addAll(scrollToTheEndToolbarAction, toggleSoftWrapsAction, clearAllAction)
    group
  }

  override def dispose(): Unit = {
    super.dispose()
    SbtShellConsoleView.removeConsoleView(project)
    EditorFactory.getInstance().releaseEditor(getConsoleEditor)
  }

}

object SbtShellConsoleView {

  private val lastConsoleViews: mutable.Map[Project, SbtShellConsoleView] = mutable.HashMap.empty

  def apply(project: Project, debugConnection: Option[RemoteConnection]): SbtShellConsoleView = {
    val cv = new SbtShellConsoleView(project, debugConnection)
    cv.getConsoleEditor.setOneLineMode(true)

    // stack trace file links
    cv.addMessageFilter(new ExceptionFilter(GlobalSearchScope.allScope(project)))
    // other file links
    cv.addMessageFilter(filePatternFilters(project))
    // url links
    new UrlFilterProvider().getDefaultFilters(project).foreach(cv.addMessageFilter)

    cv.getHistoryViewer.addEditorMouseListener(new HistoryMouseListener(cv))

    //in 2020.1 `updateUi` is invoked on toolwindow reopen, it reapplies LookAndFeel and adds default border to console editors
    forbidBorderFor(cv.getHistoryViewer)
    forbidBorderFor(cv.getConsoleEditor)

    disposeLastConsoleView(project)
    lastConsoleViews.put(project, cv)

    cv
  }

  def disposeLastConsoleView(project: Project): Unit = {
    lastConsoleViews.get(project).foreach(_.dispose())
    lastConsoleViews.remove(project)
  }

  private def removeConsoleView(project: Project): Option[SbtShellConsoleView] = {
    lastConsoleViews.remove(project)
  }

  private def filePatternFilters(project: Project) = {
    import PatternHyperlinkPart._

    def pattern(patternMacro: String) = new RegexpFilter(project, patternMacro).getPattern

    // file with line number
    val fileWithLinePattern = pattern(s"${RegexpFilter.FILE_PATH_MACROS}:${RegexpFilter.LINE_MACROS}")
    // FILE_PATH_MACROS includes a capturing group at the beginning that the format only can handle if the first linkPart is null
    val fileWithLineFormat = new PatternHyperlinkFormat(fileWithLinePattern, false, false, Collections.emptyList[String](), PATH, LINE)

    // file output without lines in messages
    val fileOnlyPattern = pattern(RegexpFilter.FILE_PATH_MACROS)
    val fileOnlyFormat = new PatternHyperlinkFormat(fileOnlyPattern, false, false, Collections.emptyList[String](), PATH)

    val dataFinder = new PatternBasedFileHyperlinkRawDataFinder(Array(fileWithLineFormat, fileOnlyFormat))
    new PatternBasedFileHyperlinkFilter(project, null, dataFinder)
  }

  private def forbidBorderFor(editor: EditorEx): Unit = {
    editor.getScrollPane.addPropertyChangeListener(new ResetBorderListener(editor))
  }

  private class HistoryMouseListener(cv: SbtShellConsoleView) extends EditorMouseListener {
    override def mouseClicked(e: EditorMouseEvent): Unit = {
      val focusManager = IdeFocusManager.getInstance(cv.getProject)
      val focusComponent = cv.getConsoleEditor.getContentComponent
      focusManager.doWhenFocusSettlesDown { () =>
        focusManager.requestFocus(focusComponent, false)
      }
    }
  }

  private class ResetBorderListener(editor: Editor) extends PropertyChangeListener {
    override def propertyChange(evt: PropertyChangeEvent): Unit = {
      if (evt.getPropertyName == "border" && evt.getNewValue != null) {
        editor.setBorder(null)
      }
    }
  }

}
