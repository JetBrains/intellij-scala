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

    val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(getProjectAdapter)
    ApplicationManager.getApplication.runWriteAction(new Runnable {
      def run() {
        libraryTable.createLibrary("SBT: org.jetbrains:some-cool-lib:0.0.1")
      }
    })
  }

  def testDoNotAnnotateIndexedDep =
    doTest(Seq.empty)
  def testDoNotAnnotateCachedDep =
    doTest(Seq.empty)
  def testAnnotateUnresolvedDep =
    doTest(Seq(Error("\"org.jetbrains\"", SbtDependencyAnnotator.ERROR_MESSAGE),
               Error("\"unknown-lib\"", SbtDependencyAnnotator.ERROR_MESSAGE),
               Error("\"0.0.0\"", SbtDependencyAnnotator.ERROR_MESSAGE)))
}
