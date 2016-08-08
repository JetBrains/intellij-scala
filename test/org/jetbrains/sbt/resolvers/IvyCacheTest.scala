package org.jetbrains.sbt
package resolvers

import com.intellij.openapi.util.SystemInfo

/**
 * @author Nikolay Obedin
 * @since 8/22/14.
 */
class IvyCacheTest extends IndexingTestCase with UsefulTestCaseHelper {

  def testIndexUpdate() = {
    implicit val p = getProject
    val resolver = new SbtIvyResolver("Test repo", "/%s/sbt/resolvers/testIvyCache" format rootPath)
    resolver.getIndex.doUpdate()
    assertIndexContentsEquals(resolver.getIndex, Set("org.jetbrains"), Set("test-one", "test-two"), Set("0.0.1", "0.0.2"))
  }

  def testNonExistentIndexUpdate() = {
    implicit val p = getProject
    if (SystemInfo.isWindows)
      assertException[InvalidRepository](Some(SbtBundle("sbt.resolverIndexer.invalidRepository","C:\\non-existent-dir"))) {
        new SbtIvyResolver("Test repo", "C:\\non-existent-dir").getIndex.doUpdate()
      }
    else
      assertException[InvalidRepository](Some(SbtBundle("sbt.resolverIndexer.invalidRepository","/non-existent-dir"))) {
        new SbtIvyResolver("Test repo", "/non-existent-dir").getIndex.doUpdate()
      }
  }
}
