package org.jetbrains.plugins
package scala

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.{DaemonCodeAnalyzerEx, DaemonCodeAnalyzerImpl, FileStatusMap}
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Nullable

package object incremental {
  private final val REASON = "Incremental highlighting"

  private val combineDirtyScopesMethod = {
    val method = classOf[FileStatusMap].getDeclaredMethod("combineDirtyScopes", classOf[Document], classOf[TextRange], classOf[Object])
    method.setAccessible(true)
    method
  }

  private val stopProcessMethod = {
    val method = classOf[DaemonCodeAnalyzerImpl].getDeclaredMethod("stopProcess", classOf[Boolean], classOf[String])
    method.setAccessible(true)
    method
  }

  private[incremental] implicit class DaemonCodeAnalyzerExt(private val daemon: DaemonCodeAnalyzer) extends AnyVal {
    // com.intellij.codeInsight.daemon.impl.FileStatusMap.combineDirtyScopes
    def combineDirtyScopes(document: Document, scope: TextRange): Unit = {
      combineDirtyScopesMethod.invoke(daemon.asInstanceOf[DaemonCodeAnalyzerEx].getFileStatusMap, document, scope, REASON)
    }

    // com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl.stopProcess
    def stopProcess(toRestartAlarm: Boolean): Unit = {
      stopProcessMethod.invoke(daemon, toRestartAlarm, REASON)
    }
  }

  private[incremental] implicit class TextRangeExt(private val a: TextRange) extends AnyVal {
    def diff(b: TextRange): TextRange = {
      if (a.isEmpty || b.contains(a)) return TextRange.EMPTY_RANGE
      if (!b.intersects(a) || (b.getStartOffset > a.getStartOffset && b.getEndOffset < a.getEndOffset)) return a
      if (b.getStartOffset <= a.getStartOffset) new TextRange(b.getEndOffset, a.getEndOffset)
      else new TextRange(a.getStartOffset, b.getStartOffset)
    }
  }

  private[incremental] implicit class EditorExt(private val that: Editor) extends AnyVal {
    // Editor.getVirtualFile is obsolete and incompatible with Diff Viewer
    @Nullable def virtualFile: VirtualFile = FileDocumentManager.getInstance.getFile(that.getDocument)
  }

  private[incremental] def isScalaIn(@Nullable file: VirtualFile): Boolean =
    file != null && (file.getExtension == "scala" || file.getExtension == "sc" || file.getExtension == "sbt" || file.getExtension == "mill")
}
