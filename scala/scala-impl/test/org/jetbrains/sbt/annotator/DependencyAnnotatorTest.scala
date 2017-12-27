package org.jetbrains.sbt
package annotator

import _root_.junit.framework.Assert._
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.scala.annotator._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.module.SbtModule
import org.jetbrains.sbt.resolvers.SbtIvyResolver
import org.jetbrains.sbt.resolvers.indexes.ResolverIndex
import org.jetbrains.sbt.resolvers.indexes.ResolverIndex.FORCE_UPDATE_KEY


/**
 * @author Nikolay Obedin
 * @since 8/4/14.
 */
class DependencyAnnotatorTest extends AnnotatorTestBase {
  private val root = s"/${TestUtils.getTestDataPath}/sbt/resolvers/testIvyCache"

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

    sys.props += FORCE_UPDATE_KEY -> "true"

    val module = {
      val moduleManager = ModuleManager.getInstance(getProject)
      val modules = moduleManager.getModules
      assertEquals(1, modules.length)
      modules(0)
    }

    val testResolver = new SbtIvyResolver("Test repo", root)
    SbtModule.setResolversTo(module, Set(testResolver))

    val index = testResolver.getIndex(myProject).get
    index.doUpdate()
  }

  override def tearDown(): Unit = {
    super.tearDown()
    sys.props -= FORCE_UPDATE_KEY
    FileUtil.delete(ResolverIndex.getIndexDirectory(root))
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
