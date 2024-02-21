package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import org.jetbrains.plugins.scala.extensions.{inReadAction, inWriteCommandAction, invokeAndWait}
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.jetbrains.plugins.scala.util.CompilerTestUtil.runWithErrorsFromCompiler
import org.junit.Assert.assertEquals

abstract class CompilerDiagnosticsTestBase extends ScalaCompilerHighlightingTestBase {

  protected def runCompilerDiagnosticsTest(
    fileName: String,
    content: String,
    expectedResult: ExpectedResult,
    expectedContent: String
  ): Unit = runWithErrorsFromCompiler(getProject) {
    val virtualFile = addFileToProjectSources(fileName, content)

    invokeAndWait {
      val descriptor = new OpenFileDescriptor(getProject, virtualFile)
      val editor = FileEditorManager.getInstance(getProject).openTextEditor(descriptor, true)
      // The tests are running in a headless environment where focus events are not propagated.
      // We need to call our listener manually.
      new CompilerHighlightingEditorFocusListener(editor).focusGained()
    }

    doAssertion(virtualFile, expectedResult)

    val highlightInfos = fetchHighlightInfos(virtualFile)
    val quickFixes = allQuickFixes(highlightInfos)
    quickFixes.foreach { fix =>
      inWriteCommandAction {
        fix.invoke(getProject, myEditor, myPsiFile)
      }(getProject)
    }

    val actualContent = inReadAction(virtualFile.findDocument.get.getText)
    assertEquals(expectedContent, actualContent)
  }

  private def allQuickFixes(highlightInfos: Seq[HighlightInfo]): Seq[IntentionAction] = inReadAction {
    val builder = Seq.newBuilder[IntentionAction]
    highlightInfos.foreach { info =>
      info.findRegisteredQuickFix { (descriptor, _) =>
        val action = descriptor.getAction
        if (action.isAvailable(getProject, myEditor, myPsiFile)) {
          builder += action
        }
      }
    }
    builder.result()
  }
}
