package org.jetbrains.plugins.scalaDirective.lang.completion

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
final class ScalaDirectiveCompletionTest extends ScalaCompletionTestBase {
  def testUsingCompletion_InsertSpacesBeforeAndAfter(): Unit = doCompletionTest(
    fileText = s"//>u$CARET",
    resultText = s"//> using $CARET",
    item = UsingDirective
  )

  def testUsingCompletion_DoNotInsertSpaceBeforeIfThereIsASpace(): Unit = doCompletionTest(
    fileText = s"//> u$CARET",
    resultText = s"//> using $CARET",
    item = UsingDirective
  )

  def testUsingCompletion_DoNotInsertSpaceBeforeIfThereIsASpace2(): Unit = doCompletionTest(
    fileText = s"//>  u$CARET",
    resultText = s"//>  using $CARET",
    item = UsingDirective
  )

  def testUsingCompletion_DoNotInsertSpaceAfterIfThereIsASpace(): Unit = doCompletionTest(
    fileText = s"//> u$CARET ",
    resultText = s"//> using $CARET",
    item = UsingDirective
  )

  def testUsingCompletion_DoNotInsertSpaceAfterIfThereIsASpace2(): Unit = doCompletionTest(
    fileText = s"//> u$CARET  ",
    resultText = s"//> using $CARET ",
    item = UsingDirective
  )
}
