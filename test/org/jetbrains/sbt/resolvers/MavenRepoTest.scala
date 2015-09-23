package org.jetbrains.sbt
package resolvers

import java.io.{IOException, File}

import com.intellij.openapi.util.SystemInfo
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase

/**
 * @author Nikolay Obedin
 * @since 8/1/14.
 */
class MavenRepoTest extends IndexingTestCase with UsefulTestCaseHelper {

  import junit.framework.Assert._

  def testIndexUpdate() = {
    val testIndex = createAndUpdateIndex(SbtResolver(SbtResolver.Kind.Maven, "Test repo", "file:/%s/sbt/resolvers/testRepository" format rootPath))
    assertIndexContentsEquals(testIndex, Set("org.jetbrains"), Set("test-one", "test-two"), Set("0.0.1", "0.0.2"))
  }

  def testNonExistentIndexUpdate() = {
    if (SystemInfo.isWindows)
      assertException[InvalidRepository](Some(SbtBundle("sbt.resolverIndexer.invalidRepository","C:\\non-existent-dir"))) {
        createAndUpdateIndex(SbtResolver(SbtResolver.Kind.Maven, "Test repo", "file:/C:/non-existent-dir"))
      }
    else
      assertException[InvalidRepository](Some(SbtBundle("sbt.resolverIndexer.invalidRepository","/non-existent-dir"))) {
        createAndUpdateIndex(SbtResolver(SbtResolver.Kind.Maven, "Test repo", "file:/non-existent-dir"))
      }
  }

  def testNonIndexedRepoUpdate() = {
    val repoUrl = "http://dl.bintray.com/scalaz/releases/"
    assertException[RemoteRepositoryHasNotBeenIndexed](Some(SbtBundle("sbt.resolverIndexer.remoteRepositoryHasNotBeenIndexed", repoUrl))) {
      createAndUpdateIndex(SbtResolver(SbtResolver.Kind.Maven, "Scalaz Bintray repo", repoUrl))
    }
  }
}
