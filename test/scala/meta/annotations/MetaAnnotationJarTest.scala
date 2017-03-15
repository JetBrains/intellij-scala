package scala.meta.annotations

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.{PsiTestUtil, TestActionEvent}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.base.DisposableScalaLibraryLoader
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert._

import scala.meta.{ScalaMetaLibrariesOwner, _}

/**
  * @author mutcianm
  * @since 14.03.17.
  */

class MetaAnnotationJarTest extends JavaCodeInsightFixtureTestCase with ScalaMetaLibrariesOwner {
  override protected def getTestDataPath: String = TestUtils.getTestDataPath + "/scalameta"
  override implicit protected def project = getProject

  override implicit protected def module = myModule

  override protected def librariesLoaders = Seq(new DisposableScalaLibraryLoader()) ++ additionalLibraries

  override def setUp() = {
    super.setUp()
    setUpLibraries()
    PsiTestUtil.addLibrary(myModule, getTestDataPath + "/addFoo_3.0.0-M7.jar")
  }

  def testLoadAnnotationFromJar(): Unit = {
    val source =
      """
        |@addFoo
        |class foo
        |""".stripMargin
    myFixture.configureByText("foo.scala", source)
    val clazz = myFixture.findClass("foo").asInstanceOf[ScClass]
    val expansion = clazz.getMetaExpansion
    expansion match {
      case Right(q"class foo { def fooBar: Int = 42 }") => // ok
      case Right(other) => fail(s"Got unexpected expansion: $other")
      case Left(error)  => fail(s"Failed to expand meta annotation: $error")
    }
    myFixture.findAllGutters().get(0).asInstanceOf[GutterIconRenderer].getClickAction.actionPerformed(new TestActionEvent())
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
    val expected = "Meta expansion failed: scala.MatchError: trait foo (of class scala.meta.Defn$Trait$DefnTraitImpl)"
    assertEquals("Wrong expansion error message", expected, errors.get(0).getDescription)
  }
}
