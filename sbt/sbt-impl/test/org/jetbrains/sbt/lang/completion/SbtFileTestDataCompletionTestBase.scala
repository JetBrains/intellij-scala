package org.jetbrains.sbt.lang.completion

import com.intellij.testFramework.{EditorTestUtil, UsefulTestCase}
import org.jetbrains.plugins.scala.lang.completion.FileTestDataCompletionTestBase
import org.jetbrains.plugins.scala.util.RevertableChange
import org.jetbrains.sbt.MockSbtBase

import scala.jdk.CollectionConverters._

abstract class SbtFileTestDataCompletionTestBase extends FileTestDataCompletionTestBase {
  self: MockSbtBase =>

  override protected lazy val caretMarker = EditorTestUtil.CARET_TAG
  override protected lazy val extension = "sbt"

  override def folderPath: String = super.folderPath + "Sbt/"

  override def doTest(): Unit = {
    // child tests contain too many completion items (more then default 500) which leads to nondeterministic test result
    // the warning is produced by IDEA:
    // Your test might miss some lookup items, because only 500 most relevant items are guaranteed to be shown in the lookup. You can:
    // 1. Make the prefix used for completion longer, so that there are less suggestions.
    // 2. Increase 'ide.completion.variant.limit' (using RegistryValue#setValue with a test root disposable).
    // 3. Ignore this warning.
    RevertableChange.withModifiedRegistryValue("ide.completion.variant.limit", 1500).run {
      super.doTest()
    }
  }

  override def checkResult(variants: Array[String],
                           expected: String): Unit =
    UsefulTestCase.assertContainsElements[String](
      asSet(variants),
      asSet(expected.split("\n"))
    )

  private def asSet(strings: Array[String]) = {
    strings.toSeq.distinct.asJava
  }
}
