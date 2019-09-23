package org.jetbrains.sbt
package lang
package completion

import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.{EditorTestUtil, UsefulTestCase}
import org.jetbrains.plugins.scala.lang.completion

import scala.collection.JavaConverters

/**
 * @author Nikolay Obedin
 * @since 7/17/14.
 */
abstract class SbtCompletionTestBase extends {
  override protected val caretMarker = EditorTestUtil.CARET_TAG
  override protected val extension = "sbt"
} with completion.CompletionTestBase {

  override def folderPath: String = super.folderPath + "Sbt/"

  override def checkResult(variants: Array[String],
                           expected: String): Unit =
    UsefulTestCase.assertContainsElements[String](
      asSet(variants),
      asSet(expected.split("\n"))
    )

  override def setUp(): Unit = {
    super.setUp()
    cleanIndices()
  }

  override def tearDown(): Unit = {
    super.tearDown()
    cleanIndices()
  }

  private def asSet(strings: Array[String]) = {
    import JavaConverters._
    strings.toSeq.distinct.asJava
  }

  private def cleanIndices(): Unit = FileUtil.delete {
    resolvers.indexes.ResolverIndex.DEFAULT_INDEXES_DIR
  }
}
