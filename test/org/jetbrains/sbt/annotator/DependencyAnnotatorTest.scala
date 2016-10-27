package org.jetbrains.sbt
package annotator

import _root_.junit.framework.Assert._
import com.intellij.openapi.module.ModuleManager
import org.jetbrains.plugins.scala.annotator._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.module.SbtModule
import org.jetbrains.sbt.resolvers.SbtIvyResolver


/**
 * @author Nikolay Obedin
 * @since 8/4/14.
 */
class DependencyAnnotatorTest extends AnnotatorTestBase {

  val testResolver = new SbtIvyResolver("Test repo", "/%s/sbt/resolvers/testIvyCache" format TestUtils.getTestDataPath)

  def testDoNotAnnotateIndexedDep(): Unit =
    doTest(Seq.empty)

  def testDoNotAnnotateIndexedDepWithDynamicVersion(): Unit =
    doTest(Seq.empty)

  def testAnnotateUnresolvedDep(): Unit = {
    val msg = SbtBundle("sbt.annotation.unresolvedDependency")
    doTest(Seq(Warning("\"org.jetbrains\"", msg),
      Warning("\"unknown-lib\"", msg),
      Warning("\"0.0.0\"", msg)))
  }

  def testAnnotateUnresolvedDepWithDynamicVersion(): Unit = {
    val msg = SbtBundle("sbt.annotation.unresolvedDependency")
    doTest(Seq(Warning("\"org.jetbrains\"", msg),
      Warning("\"unknown-lib\"", msg),
      Warning("\"latest.release\"", msg)))
  }

  override def setUp(): Unit = {
    super.setUp()


    val moduleManager = Option(ModuleManager.getInstance(getProject))
    moduleManager.foreach { manager =>
      manager.getModules.toSeq.foreach { module =>
        val resolvers = SbtModule.getResolversFrom(module)
        SbtModule.setResolversTo(module, resolvers + testResolver)
      }
    }
    testResolver.getIndex(myProject).doUpdate()(getProject)
  }

  override def tearDown(): Unit = {
    super.tearDown()
  }

  private def doTest(messages: Seq[Message]) {
    val element = loadTestFile()
    val mock = new AnnotatorHolderMock(element)
    val annotator = new SbtDependencyAnnotator

    val visitor = new ScalaRecursiveElementVisitor {
      override def visitLiteral(lit: ScLiteral) {
        annotator.annotate(lit, mock)
        super.visitLiteral(lit)
      }
    }
    element.accept(visitor)
    assertEquals(messages, mock.annotations)
  }
}
