package scala.meta
package annotations

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.{PsiTestUtil, TestActionEvent}
import org.jetbrains.plugins.scala.base.libraryLoaders.{LibraryLoader, ScalaSDKLoader}
import org.jetbrains.plugins.scala.compilation.CompilerTestUtil
import org.jetbrains.plugins.scala.compilation.CompilerTestUtil.withModifiedRegistryValue
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaBundle, ScalaVersion}
import org.junit.Assert._
import org.junit.Ignore

import java.io.File
import scala.meta.intellij.MetaExpansionsManager.PARADISE_VERSION

class MetaAnnotationJarTest_2_11 extends MetaAnnotationJarTest {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_11
}

@Ignore("meta annotation expansion doesn't work for 2.12")
class MetaAnnotationJarTest_2_12_3 extends MetaAnnotationJarTest {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12.withMinor(3)
}

@Ignore("meta annotation expansion doesn't work for 2.12")
class MetaAnnotationJarTest_2_12 extends MetaAnnotationJarTest {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12
}

@Ignore("meta annotation expansion doesn't work for 2.13")
class MetaAnnotationJarTest_2_13 extends MetaAnnotationJarTest {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13

  override def librariesLoaders: Seq[LibraryLoader] = Seq(
    ScalaSDKLoader(includeScalaReflectIntoCompilerClasspath = true)
  )
}

// TODO: remove somewhere in 2022.1 / 2022.2 SCL-19637
abstract class MetaAnnotationJarTest extends JavaCodeInsightFixtureTestCase with ScalaMetaTestBase {
  override protected def getTestDataPath: String = TestUtils.getTestDataPath + "/scalameta"

  protected lazy val testJarPath = s"/addFoo_${version.major}_$PARADISE_VERSION.jar"

  private val revertible: CompilerTestUtil.RevertableChange =
    withModifiedRegistryValue("scala.meta.annotation.expansion.legacy.support", true)

  override def setUp(): Unit = {
    super.setUp()
    revertible.applyChange()
    setUpLibraries(getModule)
    val testJar = new File(getTestDataPath + testJarPath)
    assertTrue(s"Test jar not found at $testJar", testJar.exists())
    PsiTestUtil.addLibrary(getModule, testJar.getAbsolutePath)
  }

  override def tearDown(): Unit = try {
    revertible.revertChange()
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
      case Left(error)  => throw new AssertionError(s"Failed to expand meta annotation: ${error.message}", error.cause.orNull)
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
