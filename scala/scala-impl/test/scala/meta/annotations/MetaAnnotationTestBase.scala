package scala.meta
package annotations

import java.io.File

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.{CompilerTester, PsiTestUtil}
import org.jetbrains.plugins.scala.DependencyManager
import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.debugger.{CompilationCache, ScalaCompilerTestBase}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotationsHolder, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert._

import scala.collection.JavaConverters._
import scala.meta.intellij.MetaExpansionsManager.{META_MINOR_VERSION, PARADISE_VERSION}

abstract class MetaAnnotationTestBase extends JavaCodeInsightFixtureTestCase with ScalaMetaTestBase {

  override protected def getTestDataPath: String = TestUtils.getTestDataPath + "/scalameta"
  protected var compiler: CompilerTester = _


  override def setUp(): Unit = {
    super.setUp()
    setUpLibraries(getModule)
    PsiTestUtil.addSourceRoot(getModule, myFixture.getTempDirFixture.findOrCreateDir("test"), true)
    compiler = new CompilerTester(getModule)
  }

  override def tearDown(): Unit = try {
    disposeLibraries(getModule)
    compiler.tearDown()
    ScalaCompilerTestBase.stopAndWait()
  } finally {
    compiler = null
    super.tearDown()
  }

  protected def compileMetaSource(source: String = FileUtil.loadFile(new File(getTestDataPath, s"${getTestName(false)}.scala"))): Unit = {
    addMetaSource(source)
    val cache = new CompilationCache(getModule, Seq(version.minor, META_MINOR_VERSION))
    val errors = cache.withModuleOutputCache(Iterable[String]()) {
      enableParadisePlugin()
      compiler.make()
        .asScala
        .filter(_.getCategory == CompilerMessageCategory.ERROR)
        .map(_.getMessage)
    }
    assertTrue(s"Failed to compile annotation:\n ${errors.mkString("\n")}", errors.isEmpty)
  }

  protected def compileAnnotBody(body: String): Unit = compileMetaSource(mkAnnot(annotName, body))

  protected def addMetaSource(source: String = FileUtil.loadFile(new File(getTestDataPath, s"${getTestName(false)}.scala"))): Unit = {
    myFixture.addFileToProject("src/meta.scala", source)
  }

  protected def enableParadisePlugin(): Unit = {
    val pluginArtifact = DependencyManager.resolve("org.scalameta" % s"paradise_${version.minor}" % PARADISE_VERSION)
    val profile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val settings = profile.getSettings
    assertTrue("Paradise plugin not found, aborting compilation", pluginArtifact.nonEmpty)
    settings.plugins :+= pluginArtifact.head.file.getCanonicalPath
    profile.setSettings(settings)
  }

  protected def checkNoErrorHighlights(expectedMessagePrefix: String = ""): Unit = {
    val errors = myFixture.doHighlighting(HighlightSeverity.ERROR).asScala
    val (related, unrelated) = errors.partition(_.getDescription.startsWith(expectedMessagePrefix))
    val suffix = if (unrelated.size > 1) s"\nOther errors found:\n${unrelated.mkString("\n")}" else ""
    val prefix = related.mkString("\n")
    if (related.nonEmpty)
      fail(prefix + suffix)
  }

  import intellij.psi._

  protected def checkExpansionEquals(code: String, expectedExpansion: String): Unit = {
    myFixture.configureByText(s"Usage${getTestName(false)}.scala", code)
    val holder = elementAtCaret.parentOfType(classOf[ScAnnotationsHolder], strict = false).orNull
    holder.metaExpand match {
      case Right(tree) => assertEquals(expectedExpansion, tree.toString())
      case Left(reason) if reason.nonEmpty => fail(reason)
      case Left("") => fail("Expansion was empty - did annotation even run?")
    }
  }

  protected def checkCaretResolves(): Unit = {
    val ref = refAtCaret
    val result = ref.multiResolveScala(false)
    if (result.isEmpty)
      fail(s"Reference $ref failed to resolve")
    else if (result.length > 1)
      fail(s"Reference $ref resolved to multiple elements: ${result.mkString("\n")}")
  }

  protected def checkExpandsNoError(): Unit =
    testClass.metaExpand match {
      case Left(error) => fail(s"Expansion failed: $error")
      case _ =>
    }

  protected def checkHasMember(name: String): Unit = assertTrue(s"Member $name not found", testClass.members.exists(_.getName == name))

  protected def getGutter: GutterIconRenderer = {
    val gutters = myFixture.findAllGutters()
    assertEquals("Wrong number of gutters", 1, gutters.size())
    gutters.get(0).asInstanceOf[GutterIconRenderer]
  }

  protected def createFile(text: String): Unit = myFixture.configureByText(s"$testClassName.scala", text)

  def mkAnnot(name: String, body: String): String = {
    s"""
       |import scala.meta._
       |class $name extends scala.annotation.StaticAnnotation {
       |  inline def apply(defn: Any): Any = meta {
       |    $body
       |  }
       |}
     """.stripMargin
  }

  // because getElementAtCaret from fixture forces resolve and we don't want that
  protected def elementAtCaret = PsiTreeUtil.getParentOfType(myFixture.getFile.findElementAt(myFixture.getCaretOffset-1), classOf[ScalaPsiElement])
  protected def refAtCaret = elementAtCaret match {
    case ref: ScReference => ref
    case other: PsiElement => PsiTreeUtil.getParentOfType(other, classOf[ScReference])
  }
  protected def annotName: String = s"a_${getTestName(true)}".toLowerCase
  protected def testClassName: String = getTestName(false)
  protected def testClass: ScTypeDefinition =  myFixture.getFile.getChildren
    .flatMap(_.depthFirst().collectFirst{case c: ScTypeDefinition if c.name == testClassName => c})
    .headOption
    .getOrElse {
      fail(s"Class $testClassName not found")
      throw new RuntimeException
    }
  protected val tq = "\"\"\""

}