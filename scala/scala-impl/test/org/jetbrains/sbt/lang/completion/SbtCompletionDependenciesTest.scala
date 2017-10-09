package org.jetbrains.sbt
package lang.completion

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.module.SbtModule
import org.jetbrains.sbt.resolvers.SbtIvyResolver
import org.jetbrains.sbt.resolvers.indexes.ResolverIndex

/**
 * @author Nikolay Obedin
 * @since 8/1/14.
 */
class SbtCompletionDependenciesTest extends SbtCompletionTestBase {
  private val root = s"/${TestUtils.getTestDataPath}/sbt/resolvers/testIvyCache"

  override def setUp(): Unit = {
    super.setUp()

    val testResolver = new SbtIvyResolver("Test repo", root)

    testResolver.getIndex(getProjectAdapter).get.doUpdate()(getProjectAdapter)
    val moduleManager = Option(ModuleManager.getInstance(getProjectAdapter))
    moduleManager.foreach { manager =>
      manager.getModules.toSeq.foreach { module =>
        val resolvers = SbtModule.getResolversFrom(module)
        SbtModule.setResolversTo(module, resolvers + testResolver)
      }
    }
  }

  override def tearDown(): Unit = {
    super.tearDown()

    FileUtil.delete(ResolverIndex.getIndexDirectory(root))
  }

  def testCompleteGroup(): Unit     = doTest()
  def testCompleteVersion(): Unit   = doTest()
  def testCompleteArtifact(): Unit  = doTest()
}
