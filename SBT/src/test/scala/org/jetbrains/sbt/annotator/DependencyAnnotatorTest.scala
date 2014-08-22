package org.jetbrains.sbt
package annotator

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import org.jetbrains.plugins.scala.annotator.Error
import org.jetbrains.sbt.project.module.SbtModule
import org.jetbrains.sbt.resolvers.{SbtResolver, SbtResolverIndexesManager}


/**
 * @author Nikolay Obedin
 * @since 8/4/14.
 */
class DependencyAnnotatorTest extends AnnotatorTestBase(classOf[SbtDependencyAnnotator]) {

  val testResolver = new SbtResolver(SbtResolver.Kind.Maven, "Test repo", "file:/%s/sbt/resolvers/testRepository" format baseRootPath)

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

  def testDoNotAnnotateIndexedDep =
    doTest(Seq.empty)
  def testAnnotateUnresolvedDep = {
    val msg = SbtBundle("sbt.annotation.unresolvedDependency")
    doTest(Seq(Error("\"org.jetbrains\"", msg),
               Error("\"unknown-lib\"", msg),
               Error("\"0.0.0\"", msg)))
  }
}
