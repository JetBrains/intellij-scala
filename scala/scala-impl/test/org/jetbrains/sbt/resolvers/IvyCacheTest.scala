package org.jetbrains.sbt
package resolvers

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.resolvers.indexes.ResolverIndex
import org.jetbrains.sbt.resolvers.indexes.ResolverIndex.FORCE_UPDATE_KEY

/**
 * @author Nikolay Obedin
 * @since 8/22/14.
 */
class IvyCacheTest extends IndexingTestCase with UsefulTestCaseHelper {
  private val root = s"/${TestUtils.getTestDataPath + "/"}/sbt/resolvers/testIvyCache"

  def testIndexUpdate(): Unit = {
    val resolver = new SbtIvyResolver("Test repo", root)
    val index = resolver.getIndex(project).get
    index.doUpdate()
    assertIndexContentsEquals(index, Set("org.jetbrains"), Set("test-one", "test-two"), Set("0.0.1", "0.0.2"))
  }

  def testNonExistentIndexUpdate(): Unit = {
    if (SystemInfo.isWindows)
      assertException[InvalidRepository](Some(SbtBundle("sbt.resolverIndexer.invalidRepository","C:\\non-existent-dir"))) {
        new SbtIvyResolver("Test repo", "C:\\non-existent-dir").getIndex(project).get.doUpdate()
      }
    else
      assertException[InvalidRepository](Some(SbtBundle("sbt.resolverIndexer.invalidRepository","/non-existent-dir"))) {
        new SbtIvyResolver("Test repo", "/non-existent-dir").getIndex(project).get.doUpdate()
      }
  }

  override def setUp(): Unit = {
    super.setUp()
    sys.props += FORCE_UPDATE_KEY -> "true"
  }

  override def tearDown(): Unit = {
    super.tearDown()
    sys.props -= FORCE_UPDATE_KEY
    FileUtil.delete(ResolverIndex.getIndexDirectory(root))
  }
}
