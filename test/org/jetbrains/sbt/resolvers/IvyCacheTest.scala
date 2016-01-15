package org.jetbrains.sbt
package resolvers

import com.intellij.openapi.util.SystemInfo

/**
 * @author Nikolay Obedin
 * @since 8/22/14.
 */
class IvyCacheTest extends IndexingTestCase with UsefulTestCaseHelper {

  def testIndexUpdate() = {
    val testIndex = createAndUpdateIndex(
      new SbtResolver(SbtResolver.Kind.Ivy, "Test repo", "/%s/sbt/resolvers/testIvyCache" format rootPath))
    assertIndexContentsEquals(testIndex, Set("org.jetbrains"), Set("test-one", "test-two"), Set("0.0.1", "0.0.2"))
  }

  def testNonExistentIndexUpdate() = {
    if (SystemInfo.isWindows)
      assertException[InvalidRepository](Some(SbtBundle("sbt.resolverIndexer.invalidRepository","C:\\non-existent-dir"))) {
        createAndUpdateIndex(SbtResolver(SbtResolver.Kind.Ivy, "Test repo", "C:\\non-existent-dir"))
      }
    else
      assertException[InvalidRepository](Some(SbtBundle("sbt.resolverIndexer.invalidRepository","/non-existent-dir"))) {
        createAndUpdateIndex(SbtResolver(SbtResolver.Kind.Ivy, "Test repo", "/non-existent-dir"))
      }
  }
}
