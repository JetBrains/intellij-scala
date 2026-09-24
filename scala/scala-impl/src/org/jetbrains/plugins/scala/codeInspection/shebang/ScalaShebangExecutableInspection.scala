package org.jetbrains.plugins.scala.codeInspection.shebang

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInspection.{InspectionManager, LocalInspectionTool, LocalQuickFix, LocalQuickFixAndIntentionActionOnPsiElement, ProblemDescriptor, ProblemHighlightType}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiComment, PsiDocumentManager, PsiElement, PsiFile}
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import java.io.File

// Similar to Kotlin's ScriptShebangExecPermissionInspection:
// https://github.com/JetBrains/intellij-community/blob/43f59a59e0360809fb08461e1023c725b27358b3/plugins/kotlin/code-insight/inspections-k2/src/org/jetbrains/kotlin/idea/codeInsight/inspections/ScriptShebangExecPermissionInspection.kt
final class ScalaShebangExecutableInspection extends LocalInspectionTool with DumbAware {
  import ProblemDescriptor.EMPTY_ARRAY
  import ScalaShebangExecutableInspection._

  override def isEnabledByDefault: Boolean = true

  override def checkFile(
    file: PsiFile,
    manager: InspectionManager,
    isOnTheFly: Boolean
  ): Array[ProblemDescriptor] = {
    val scalaFile = file match {
      case scalaFile: ScalaFile => scalaFile
      case _ =>
        return EMPTY_ARRAY
    }
    val comment = findShebangComment(scalaFile)
    if (comment == null)
      return EMPTY_ARRAY

    val script = findNonExecutableLocalScript(scalaFile)
    if (script == null)
      return EMPTY_ARRAY

    val quickFix = new MakeExecutableQuickFix(comment, script)
    Array(manager.createProblemDescriptor(
      comment,
      ScalaInspectionBundle.message("shebang.not.executable"),
      isOnTheFly,
      Array[LocalQuickFix](quickFix),
      ProblemHighlightType.WARNING
    ))
  }
}

object ScalaShebangExecutableInspection {
  // Scala uses SCL-25035's ordinary first-character comment PSI because it has no script-file PSI or shebang token.
  @Nullable
  private def findNonExecutableLocalScript(file: ScalaFile): File = {
    val virtualFile = file.getVirtualFile
    if (virtualFile == null || !virtualFile.isInLocalFileSystem)
      return null
    // The PSI text starts after the BOM, but SCL-25035 recognizes a shebang only at byte offset zero.
    if (virtualFile.getBOM != null)
      return null

    val script = VfsUtilCore.virtualToIoFile(virtualFile)
    if (script.canExecute)
      return null

    script
  }

  // SCL-25035 recognizes a shebang only when "#!" is the first two characters in the file.
  // Keep this inspection bound to that existing comment PSI rather than interpreting arbitrary text as a script header.
  @Nullable
  private def findShebangComment(file: ScalaFile): PsiComment = {
    val firstElement = file.findElementAt(0)
    firstElement match {
      case comment: PsiComment if isShebangComment(comment) => comment
      case _ => null
    }
  }

  private def isShebangComment(comment: PsiComment): Boolean = {
    val textRange = comment.getTextRange
    val text = comment.getText
    textRange.getStartOffset == 0 && text.startsWith("#!")
  }

  private[codeInspection] def makeScriptExecutable(
    script: File
  ): Either[String, Unit] = {
    try {
      val executable = script.setExecutable(true)
      if (executable)
        Right(())
      else
        Left(ScalaInspectionBundle.message("shebang.make.executable.file.system.rejected"))
    } catch {
      case _: SecurityException =>
        Left(ScalaInspectionBundle.message("shebang.make.executable.security.exception"))
    }
  }

  /**
   * Shows a short-lived error hint above the shebang range, so a quick-fix failure stays at its source.
   * This follows the editor-hint UX in ScalaActionUtil.showHint and the range-aware platform API:
   * https://github.com/JetBrains/intellij-community/blob/43f59a59e0360809fb08461e1023c725b27358b3/platform/platform-api/src/com/intellij/codeInsight/hint/HintManager.java
   *
   * The hint is deferred until the quick-fix action finishes, so its invocation does not dismiss it.
   *
   * Inspection Results can invoke quick fixes without an editor. In that case, the caller uses the
   * selected editor only when it displays this same file; otherwise contextual feedback is unavailable.
   */
  private def showMakeExecutableFailure(
    project: Project,
    editor: Editor,
    textRange: TextRange,
    message: String
  ): Unit = {
    ApplicationManager.getApplication().invokeLater(
      () => {
        if (!editor.isDisposed) {
          showMakeExecutableFailureHint(editor, message, textRange)
        }
      },
      project.getDisposed
    )
  }

  @RequiresEdt
  private def showMakeExecutableFailureHint(
    editor: Editor,
    message: String,
    textRange: TextRange
  ): Unit = {
    HintManager.getInstance().showErrorHint(
      editor,
      message,
      textRange.getStartOffset,
      textRange.getEndOffset,
      HintManager.ABOVE,
      HintManager.HIDE_BY_ANY_KEY |
        HintManager.HIDE_BY_TEXT_CHANGE |
        HintManager.HIDE_BY_SCROLLING,
      0
    )
  }

  @Nullable
  private def findSelectedEditorFor(
    project: Project,
    file: PsiFile
  ): Editor = {
    // LocalQuickFixAndIntentionActionOnPsiElement has no invocation editor from Inspection Results.
    // Reuse the selected editor only when it displays the affected file, so the hint stays contextual.
    val document = PsiDocumentManager.getInstance(project).getDocument(file)
    val selectedEditor = FileEditorManager.getInstance(project).getSelectedTextEditor
    if (selectedEditor != null && selectedEditor.getDocument == document)
      selectedEditor
    else
      null
  }

  private[codeInspection] final class MakeExecutableQuickFix(
    shebang: PsiComment,
    script: File
  ) extends LocalQuickFixAndIntentionActionOnPsiElement(shebang) {

    override def getText: String =
      ScalaInspectionBundle.message("shebang.make.executable")

    override def getFamilyName: String =
      ScalaInspectionBundle.message("shebang.make.executable")

    override def invoke(
      project: Project,
      file: PsiFile,
      @Nullable editor: Editor,
      startElement: PsiElement,
      endElement: PsiElement
    ): Unit = {
      val textRange = startElement.getTextRange
      val permissionChange = makeScriptExecutable(script)
      permissionChange match {
        case Right(_) =>
          // File.setExecutable changes filesystem metadata outside PSI, so restart analysis to remove the warning.
          DaemonCodeAnalyzer.getInstance(project).restart(file, this)
        case Left(message) =>
          val failureEditor =
            if (editor != null)
              editor
            else
              findSelectedEditorFor(project, file)
          if (failureEditor != null) {
            showMakeExecutableFailure(project, failureEditor, textRange, message)
          }
      }
    }

    override def startInWriteAction: Boolean = false
  }
}
