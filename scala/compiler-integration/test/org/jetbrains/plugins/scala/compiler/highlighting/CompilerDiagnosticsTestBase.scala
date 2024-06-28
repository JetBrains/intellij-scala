package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.plugins.scala.extensions.{inReadAction, inWriteCommandAction}
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.jetbrains.plugins.scala.util.CompilerTestUtil.runWithErrorsFromCompiler
import org.junit.Assert.assertEquals

trait CompilerDiagnosticsTestBase { self: ScalaCompilerHighlightingTestBase =>

  protected def runCompilerDiagnosticsTest(
    fileName: String,
    content: String,
    expectedResult: ExpectedResult,
    expectedContent: String
  ): Unit = runWithErrorsFromCompiler(getProject) {
    val virtualFile = addFileToProjectSources(fileName, content)

    waitUntilFileIsHighlighted(virtualFile)

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
