package org.jetbrains.sbt
package lang.completion

import com.intellij.openapi.module.ModuleManager
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.module.SbtModule
import org.jetbrains.sbt.resolvers.SbtIvyResolver

/**
 * @author Nikolay Obedin
 * @since 8/1/14.
 */
class CompletionDependenciesTest extends CompletionTestBase {

  val testResolver = new SbtIvyResolver("Test repo", "/%s/sbt/resolvers/testIvyCache" format TestUtils.getTestDataPath)

  override def setUp() = {
    super.setUp()
    testResolver.getIndex.doUpdate()(getProjectAdapter)
    val moduleManager = Option(ModuleManager.getInstance(getProjectAdapter))
    moduleManager.foreach { manager =>
      manager.getModules.toSeq.foreach { module =>
        val resolvers = SbtModule.getResolversFrom(module)
        SbtModule.setResolversTo(module, resolvers + testResolver)
      }
    }
  }

  def testCompleteArtifact()  = doTest()
  def testCompleteGroup()     = doTest()
  def testCompleteVersion()   = doTest()
}
