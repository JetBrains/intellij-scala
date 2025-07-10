package org.jetbrains.plugins.scala
package incremental

import extensions.{&, FirstChild, PsiElementExt}
import lang.psi.api.expr.{ScArgumentExprList, ScBlock, ScParenthesisedExpr}
import project.ProjectExt
import startup.ProjectActivity
import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DaemonListener
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.openapi.editor.markup.{HighlighterLayer, HighlighterTargetArea, RangeHighlighter, TextAttributes}
import com.intellij.openapi.editor.{Editor, EditorFactory}
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.{PsiElement, PsiManager}
import com.intellij.ui.{Gray, JBColor}
import com.intellij.util.ui.StartupUiUtil

import java.awt.Color
import java.util
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.matching.Regex

object Tracing {
  private val MaxLength = 120

  private val MultipleSpaces = new Regex(" {2,}")

  class StartupActivity extends ProjectActivity {
    override def execute(project: Project): Unit = {
      val connection = project.getMessageBus.connect(project.unloadAwareDisposable)
      connection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new HighlightingListener(project))
    }
  }

  private val TRACING_HIGHLIGHTER_KEY = Key.create[AnyRef]("tracing_highlighter_key")

  private val RESOLVE_STATE_KEY = Key.create[Either[RangeHighlighter, Unit]]("resolve_state_key")
  private val INFERENCE_STATE_KEY = Key.create[Either[RangeHighlighter, Unit]]("inference_state_key")

  private val RESOLVE_COLOR = lighter(JBColor.RED)
  private val INFERENCE_COLOR = lighter(JBColor.YELLOW)

  private def lighter(color: Color): Color = if (StartupUiUtil.INSTANCE.isDarkTheme) color.darker() else color.brighter()

  private def isHighlightingTracingEnabled: Boolean = Registry.is("scala.highlighting.tracing")

  private class HighlightingListener(project: Project) extends DaemonListener {
    private var startInstants = Map.empty[FileEditor, Long]
    private var durations = Seq.empty[Long]

    override def daemonStarting(fileEditors: util.Collection[_ <: FileEditor]): Unit = {
      if (!isHighlightingTracingEnabled) return
      val editors = fileEditors.asScala.filter(e => isScalaIn(e.getFile))
      if (editors.isEmpty) return
      cleanElementStates()
      startInstants ++= editors.map(editor => editor -> System.nanoTime())
      durations = Seq.empty
      statusBar.setInfo("Highlighting...")
    }

    override def daemonFinished(fileEditors: util.Collection[_ <: FileEditor]): Unit = {
      Highlighting.suppress = false
      if (!isHighlightingTracingEnabled) return
      val editors = fileEditors.asScala.filter(e => isScalaIn(e.getFile))
      if (editors.isEmpty) return
      val ds = editors.map(editor => (System.nanoTime() - startInstants(editor)) / 1000000)
      statusBar.setInfo("Highlighted: " + (durations ++ ds).map(x => s"$x ms").mkString(", "))
      startInstants --= editors.toSet
      durations ++= ds
      mergeTracingHighlighters()
    }

    private def statusBar = WindowManager.getInstance.getStatusBar(project)
  }

  def trace(e: PsiElement, reason: String, start: Boolean = false): Unit = if (isHighlightingTracingEnabled) {
    VisibleRange.editorsFor(e).foreach { editor =>
      reason match {
        case "Resolve" => highlightElement(editor, e, start, RESOLVE_STATE_KEY, RESOLVE_COLOR)
        case "Inference" => highlightElement(editor, e, start, INFERENCE_STATE_KEY, INFERENCE_COLOR)
        case _ =>
      }

      if (!start) {
        val text = reason + ": " + {
          val range = e.getTextRange
          val charSequence = editor.getDocument.getCharsSequence.subSequence(range.getStartOffset, range.getEndOffset.min(range.getStartOffset + MaxLength))
          val s = MultipleSpaces.replaceAllIn(charSequence.toString.replace('\n', '↵'), " ")
          if (range.getLength > MaxLength) s + "…" else s
        }

        val highlighter = editor.getMarkupModel.addRangeHighlighter(
          e.getTextRange.getStartOffset, e.getTextRange.getEndOffset, HighlighterLayer.ADDITIONAL_SYNTAX, null, HighlighterTargetArea.EXACT_RANGE)

        highlighter.setErrorStripeMarkColor(new JBColor(Gray._170, Gray._80))
        highlighter.setThinErrorStripeMark(true)
        highlighter.setErrorStripeTooltip(text)
        highlighter.putUserData(TRACING_HIGHLIGHTER_KEY, "")

//        println(text)
      }
    }
  }

  private def highlightElement(editor: Editor, e: PsiElement, start: Boolean, key: Key[Either[RangeHighlighter, Unit]], color: Color): Unit = e.synchronized {
    val state = e.getUserData(key)

    state match {
      case Right(_) => return
      case Left(_) if start => return
      case _ =>
    }

    e match {
      case _: ScParenthesisedExpr | _: ScBlock | (_: ScArgumentExprList) & FirstChild(_: ScBlock) =>
      case _ =>
        if (start) {
          val highlighter = {
            val attributes = new TextAttributes(null, color, null, null, 0)
            editor.getMarkupModel.addRangeHighlighter(e.getTextRange.getStartOffset, e.getTextRange.getEndOffset,
              HighlighterLayer.ADDITIONAL_SYNTAX, attributes, HighlighterTargetArea.EXACT_RANGE)
          }
          highlighter.putUserData(key, Left(null))
          e.putUserData(key, Left(highlighter))
        } else {
          state match {
            case Left(h) => h.dispose()
            case _ =>
          }
          e.putUserData(key, Right(()))
        }
    }
  }

  private def mergeTracingHighlighters(): Unit = {
    EditorFactory.getInstance.getAllEditors.foreach { editor =>
      editor.getMarkupModel.getAllHighlighters.filter(_.getUserData(TRACING_HIGHLIGHTER_KEY) != null)
        .groupBy(h => (h.getTextRange, h.getErrorStripeTooltip))
        .values
        .foreach(_.tail.foreach(_.dispose()))
    }
  }

  private def cleanElementStates(): Unit = {
    val editors = EditorFactory.getInstance.getAllEditors
    val editorsWithVFiles = editors.map(editor => editor -> editor.getVirtualFile).filter(_._2 != null)
    editorsWithVFiles.foreach { case (editor, vFile) => cleanElementStateInEditor(editor, vFile) }
  }

  private def cleanElementStateInEditor(editor: Editor, vFile: VirtualFile): Unit = {
    val psiFile = PsiManager.getInstance(editor.getProject).findFile(vFile)
    if (psiFile == null)
      return

    val dirtyRange = Option(DaemonCodeAnalyzer.getInstance(editor.getProject).asInstanceOf[DaemonCodeAnalyzerEx].getFileStatusMap
      .getFileDirtyScope(editor.getDocument, psiFile, Pass.UPDATE_ALL)).getOrElse(TextRange.EMPTY_RANGE)

    psiFile.elements(_.getTextRange.intersects(dirtyRange)).foreach { e =>
      Seq(RESOLVE_STATE_KEY, INFERENCE_STATE_KEY).foreach { key =>
        e.getUserData(key) match {
          case Left(h) => h.dispose()
          case _ =>
        }
        e.putUserData(key, null)
      }
    }
  }
}
