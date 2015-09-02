package org.jetbrains.plugins.scala.debugger.exactBreakpoints

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.SourcePositionHighlighter
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.DocumentUtil
import com.intellij.xdebugger.XDebuggerUtil
import org.jetbrains.plugins.scala.debugger.{ScalaDebuggerTestCase, ScalaPositionManager}
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.junit.Assert

import scala.collection.JavaConverters._

/**
 * @author Nikolay.Tropin
 */
abstract class ExactBreakpointTestBase extends ScalaDebuggerTestCase {
  protected val mainClass = "Sample"
  protected val fileName = s"$mainClass.scala"

  case class Breakpoint(line: Int, ordinal: Integer) {
    override def toString: String = s"line = $line, ordinal=$ordinal"
  }

  private def addBreakpoint(b: Breakpoint): Unit = addBreakpoint(fileName, b.line, b.ordinal)

  protected def checkVariants(lineNumber: Int, variants: String*) = {
    val xSourcePosition = XDebuggerUtil.getInstance().createPosition(getVirtualFile(getFileInSrc(fileName)), lineNumber)
    val foundVariants = scalaLineBreakpointType.computeVariants(getProject, xSourcePosition).asScala.map(_.getText)
    Assert.assertEquals("Wrong set of variants found: ", variants, foundVariants)
  }

  protected def checkStoppedAtBreakpointAt(breakpoints: Breakpoint*)(sourcePositionText: String) = {
    checkStopResumeSeveralTimes(breakpoints: _*)(sourcePositionText)
  }

  protected def checkStopResumeSeveralTimes(breakpoints: Breakpoint*)(sourcePositions: String*) = {
    def message(expected: String, actual: String) = {
      s"Wrong source position. Expected: $expected, actual: $actual"
    }
    breakpoints.foreach(addBreakpoint)
    runDebugger(mainClass) {
      for (expected <- sourcePositions) {
        waitForBreakpoint()
        managed {
          val location = suspendContext.getFrameProxy.getStackFrame.location
          inReadAction {
            val sourcePosition = new ScalaPositionManager(getDebugProcess).getSourcePosition(location)
            val text: String = highlightedText(sourcePosition)
            Assert.assertTrue(message(expected, text), text.startsWith(expected.stripSuffix("...")))
          }
        }
        resume()
      }
    }
  }

  private def highlightedText(sourcePosition: SourcePosition): String = {
    val elemRange = SourcePositionHighlighter.getHighlightRangeFor(sourcePosition)
    val document = PsiDocumentManager.getInstance(getProject).getDocument(sourcePosition.getFile)
    val lineRange = DocumentUtil.getLineTextRange(document, sourcePosition.getLine)
    val textRange = if (elemRange != null) elemRange.intersection(lineRange) else lineRange
    document.getText(textRange).trim
  }

  protected def checkNotStoppedAtBreakpointAt(breakpoint: Breakpoint) = {
    addBreakpoint(breakpoint)
    runDebugger(mainClass) {
      Assert.assertTrue(s"Stopped at breakpoint: $breakpoint", processTerminatedNoBreakpoints())
    }
  }

  protected def addFileToProject(fileText: String): Unit = {
    Assert.assertTrue(s"File should start with `object $mainClass`", fileText.startsWith(s"object $mainClass"))
    addFileToProject(fileName, fileText.stripMargin.trim)
  }
}