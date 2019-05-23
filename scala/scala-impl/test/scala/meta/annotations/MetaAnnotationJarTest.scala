package scala.meta
package annotations

import java.io.File

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.{PsiTestUtil, TestActionEvent}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert._

import scala.meta.intellij.MetaExpansionsManager.PARADISE_VERSION

/**
  * @author mutcianm
  * @since 14.03.17.
  */

class MetaAnnotationJarTest extends JavaCodeInsightFixtureTestCase with ScalaMetaTestBase {
  override protected def getTestDataPath: String = TestUtils.getTestDataPath + "/scalameta"

  protected lazy val testJarPath = s"/addFoo_${version.major}_$PARADISE_VERSION.jar"

  override def setUp(): Unit = {
    super.setUp()
    setUpLibraries(getModule)
    val testJar = new File(getTestDataPath + testJarPath)
    assertTrue(s"Test jar not found at $testJar", testJar.exists())
    PsiTestUtil.addLibrary(getModule, testJar.getAbsolutePath)
  }

  override def tearDown(): Unit = try {
    disposeLibraries(getModule)
  } finally {
    inWriteAction {
      val projectJdkTable = ProjectJdkTable.getInstance()
      projectJdkTable.getAllJdks.foreach(projectJdkTable.removeJdk)
    }
    super.tearDown()
  }

  def testLoadAnnotationFromJar(): Unit = {
    import intellij.psi._
    val source =
      """
        |@addFoo
        |class foo
        |""".stripMargin
    myFixture.configureByText("foo.scala", source)
    val clazz = myFixture.findClass("foo").asInstanceOf[ScClass]
    clazz.metaExpand match {
      case Right(q"class foo { def fooBar: Int = 42 }") => // ok
      case Right(other) => fail(s"Got unexpected expansion: $other")
      case Left(error)  => fail(s"Failed to expand meta annotation: $error")
    }
  }

  def testGutterClickExpansion(): Unit = {
    val source =
      """
        |@addFoo
        |class foo
        |""".stripMargin
    myFixture.configureByText("foo.scala", source)
    val allGutters = myFixture.findAllGutters()
    assert(allGutters.size() == 1, s"Wrong number of gutters, expected 1, got ${allGutters.size()}")
    val iconRenderer = allGutters.get(0).asInstanceOf[GutterIconRenderer]
    assertEquals("Wrong caption on gutter icon", ScalaBundle.message("scala.meta.expand"), iconRenderer.getTooltipText)
    iconRenderer.getClickAction.actionPerformed(new TestActionEvent())
    val expected = "class foo {\n  def fooBar: Int = 42\n}"
    val actual = myFixture.findClass("foo").getText
    assertEquals("Unexpected expansion after click", expected, actual)
  }

  def testAnnotationErrorReporting(): Unit = {
    val source =
      """
        |@addFoo
        |trait foo
        |""".stripMargin
    myFixture.configureByText("foo.scala", source)
    val errors = myFixture.doHighlighting(HighlightSeverity.ERROR)
    assertEquals("Wrong number of reported expansion errors", 1, errors.size())
    val expected = "Macro expansion failed: scala.MatchError: trait foo (of class scala.meta.Defn$Trait$DefnTraitImpl)"
    assertEquals("Wrong expansion error message", expected, errors.get(0).getDescription)
  }
}
