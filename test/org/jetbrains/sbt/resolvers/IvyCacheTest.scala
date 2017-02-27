package org.jetbrains.sbt
package resolvers

import com.intellij.openapi.util.SystemInfo
import org.jetbrains.plugins.scala.util.TestUtils

/**
 * @author Nikolay Obedin
 * @since 8/22/14.
 */
class IvyCacheTest extends IndexingTestCase with UsefulTestCaseHelper {
  private val rootPath = TestUtils.getTestDataPath + "/"

  def testIndexUpdate(): Unit = {
    val resolver = new SbtIvyResolver("Test repo", "/%s/sbt/resolvers/testIvyCache" format rootPath)
    resolver.getIndex(project).doUpdate()
    assertIndexContentsEquals(resolver.getIndex(project), Set("org.jetbrains"), Set("test-one", "test-two"), Set("0.0.1", "0.0.2"))
  }

  def testNonExistentIndexUpdate(): Unit = {
    if (SystemInfo.isWindows)
      assertException[InvalidRepository](Some(SbtBundle("sbt.resolverIndexer.invalidRepository","C:\\non-existent-dir"))) {
        new SbtIvyResolver("Test repo", "C:\\non-existent-dir").getIndex(project).doUpdate()
      }
    else
      assertException[InvalidRepository](Some(SbtBundle("sbt.resolverIndexer.invalidRepository","/non-existent-dir"))) {
        new SbtIvyResolver("Test repo", "/non-existent-dir").getIndex(project).doUpdate()
      }
  }
}
