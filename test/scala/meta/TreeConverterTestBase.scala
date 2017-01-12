package scala.meta

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion
import org.junit.Assert.{assertEquals, assertTrue}

import scala.meta.semantic.IDEAContext

abstract class TreeConverterTestBase extends ScalaLightCodeInsightFixtureTestAdapter {

  implicit protected def implicitFixture: CodeInsightTestFixture = fixture
  implicit protected val semanticContext = new IDEAContext(fixture.getProject) {
    override def dumbMode: Boolean = true
  }

  def doTest(text: String, tree: Tree) = {
    import TreeConverterTestUtils._

    val converted = convert(text)

    if (!structuralEquals(converted, tree)) {
      assertEquals("Trees not equal", tree.toString(), converted.toString())
      assertTrue(false)
    }

    assertEquals("Text comparison failure", tree.toString(), converted.toString())
  }

  //  def testOk() = () // to get rid of no tests found spam in IDEA junit runner
}

abstract class TreeConverterTestBaseNoLibrary extends TreeConverterTestBase {
  override def loadScalaLibrary = false

  //  override def testOk() = () // to get rid of no tests found spam in IDEA junit runner
}

abstract class TreeConverterTestBaseWithLibrary extends TreeConverterTestBase {
  override protected val scalaSdkVersion: ScalaSdkVersion = ScalaSdkVersion._2_11
  //  override def testOk() = () // to get rid of no tests found spam in IDEA junit runner
}
