package org.jetbrains.sbt
package lang.completion

import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.scala.lang.completion
import org.jetbrains.sbt.resolvers.indexes.ResolverIndex

import scala.collection.JavaConverters._

/**
 * @author Nikolay Obedin
 * @since 7/17/14.
 */

abstract class SbtCompletionTestBase extends completion.CompletionTestBase {

  override def folderPath: String = super.folderPath + "Sbt/"
  override def testFileExt = ".sbt"

  override def checkResult(_got: Array[String], _expected: String) {
    val got = _got.distinct.toSeq.asJava
    val expected = _expected.split("\n").distinct.toSeq.asJava
    UsefulTestCase.assertContainsElements[String](got, expected)
  }

  override def setUp() {
    super.setUp()
    inWriteAction {
      StartupManager.getInstance(getProjectAdapter) match {
        case manager: StartupManagerImpl => manager.startCacheUpdate()
      }
    }
    FileUtil.delete(ResolverIndex.DEFAULT_INDEXES_DIR)
  }

  override def tearDown(): Unit = {
    super.tearDown()
    FileUtil.delete(ResolverIndex.DEFAULT_INDEXES_DIR)
  }
}
