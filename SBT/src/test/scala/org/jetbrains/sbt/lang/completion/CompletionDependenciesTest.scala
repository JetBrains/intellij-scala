package org.jetbrains.sbt
package lang.completion

import com.intellij.openapi.module.ModuleManager
import org.jetbrains.sbt.project.module.SbtModule
import org.jetbrains.sbt.resolvers.{SbtResolver, SbtResolverIndexesManager}

/**
 * @author Nikolay Obedin
 * @since 8/1/14.
 */
class CompletionDependenciesTest extends CompletionTestBase {

  val testResolver = new SbtResolver("Test repo", "file:/%s/sbt/resolvers/testRepository" format baseRootPath)

  override def setUp() = {
    super.setUp()
    SbtResolverIndexesManager().update(Seq(testResolver))
    val moduleManager = Option(ModuleManager.getInstance(getProjectAdapter))
    moduleManager.foreach { manager =>
      manager.getModules.toSeq.foreach { module =>
        val resolvers = SbtModule.getResolversFrom(module)
        SbtModule.setResolversTo(module, resolvers + testResolver)
      }
    }
  }

  def testCompleteArtifact =
    doTest()
  def testCompleteGroup =
    doTest()
  def testCompleteVersion =
    doTest()
}
