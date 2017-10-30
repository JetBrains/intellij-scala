package scala.meta.annotations

import java.io.File

import com.intellij.ProjectTopics
import com.intellij.compiler.server.BuildManager
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.module.{JavaModuleType, Module}
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.{ModuleRootEvent, ModuleRootListener}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.{PsiTestUtil, VfsTestUtil}
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.base.DisposableScalaLibraryLoader
import org.jetbrains.plugins.scala.base.libraryLoaders.LibraryLoader
import org.jetbrains.plugins.scala.debugger.{CompilationCache, ScalaVersion}
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, inWriteAction}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert
import org.junit.Assert.fail
import org.junit.experimental.categories.Category

import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.meta.ScalaMetaLibrariesOwner.MetaBaseLoader
import scala.meta.{Compilable, ScalaMetaLibrariesOwner}

@Category(Array(classOf[SlowTests]))
abstract class MetaAnnotationTestBase extends JavaCodeInsightFixtureTestCase with ScalaMetaLibrariesOwner with Compilable {

  import MetaAnnotationTestBase._

  override implicit def project: Project = getProject
  override implicit protected def module: Module = metaModule
  var metaModule: Module = _
  override def rootProject = getProject
  override def rootModule = myModule

  private lazy val metaDirectory = inWriteAction {
    val baseDir = project.getBaseDir
    baseDir.findChild("meta") match {
      case null => baseDir.createChildDirectory(null, "meta")
      case directory => directory
    }
  }

  override protected def getTestDataPath: String = TestUtils.getTestDataPath + "/scalameta"

  override def librariesLoaders: Seq[LibraryLoader] =
    Seq(new DisposableScalaLibraryLoader()) ++ additionalLibraries

  override def setUp(): Unit = {
    super.setUp()

    project.getMessageBus
      .connect(getTestRootDisposable)
      .subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener {
        override def rootsChanged(event: ModuleRootEvent) {
          BuildManager.getInstance.clearState(project)
        }
      })

    metaModule = PsiTestUtil.addModule(project, JavaModuleType.getModuleType, "meta", metaDirectory)
    addRoots(metaModule)
    addRoots(myModule)
    setUpLibraries()

    inWriteAction {
      val modifiableRootModel = myModule.modifiableModel
      modifiableRootModel.addModuleOrderEntry(metaModule)
      modifiableRootModel.commit()
    }
  }

  override def tearDown(): Unit = try {
    disposeLibraries()

    inWriteAction {
      val jdkTable = ProjectJdkTable.getInstance()
      jdkTable.getAllJdks.foreach(jdkTable.removeJdk)
    }
  } finally {
    metaModule = null
    super.tearDown()
  }

  protected def compileMetaSource(source: String = FileUtil.loadFile(new File(getTestDataPath, s"${getTestName(false)}.scala"))): List[String] = {
    addMetaSource(source)
    val cache = new CompilationCache(metaModule, Seq(version.minor, ScalaMetaLibrariesOwner.metaVersion))
    cache.withModuleOutputCache(List[String]()) {
      setUpCompiler
      enableParadisePlugin()
      runMake()
    }
  }

  protected def compileAnnotBody(body: String) = compileMetaSource(mkAnnot(annotName, body))

  protected def addMetaSource(source: String = FileUtil.loadFile(new File(getTestDataPath, s"${getTestName(false)}.scala"))): Unit = {
    VfsTestUtil.createFile(metaDirectory, "meta.scala", source)
  }

  protected def enableParadisePlugin(): Unit = {
    val profile = ScalaCompilerConfiguration.instanceIn(project).defaultProfile
    val settings = profile.getSettings
    val path = MetaParadiseLoader()(metaModule).path
    assert(new File(path).exists(), "Paradise plugin not found, aborting compilation")
    settings.plugins :+= path
    profile.setSettings(settings)
  }

  protected def checkNoErrorHighlights(expectedMessagePrefix: String = ""): Unit = {
    val errors = myFixture.doHighlighting(HighlightSeverity.ERROR).asScala
    val (related, unrelated) = errors.partition(_.getDescription.startsWith(expectedMessagePrefix))
    val suffix = if (unrelated.size > 1) s"\nOther errors found:\n${unrelated.mkString("\n")}" else ""
    val prefix = related.mkString("\n")
    if (related.nonEmpty)
      Assert.fail(prefix + suffix)
  }

  protected def checkExpansionEquals(code: String, expectedExpansion: String): Unit = {
    import scala.meta.intellij.psiExt._
    myFixture.configureByText(s"Usage${getTestName(false)}.scala", code)
    val holder = ScalaPsiUtil.getParentOfType(elementAtCaret, classOf[ScAnnotationsHolder]).asInstanceOf[ScAnnotationsHolder]
    holder.getMetaExpansion match {
      case Right(tree) => Assert.assertEquals(expectedExpansion, tree.toString())
      case Left(reason) if reason.nonEmpty => Assert.fail(reason)
      case Left("") => Assert.fail("Expansion was empty - did annotation even run?")
    }
  }

  protected def checkExpandsNoError(): Unit = {
    import scala.meta.intellij.psiExt._
    testClass.getMetaExpansion match {
      case Left(error)  => fail(s"Expansion failed: $error")
      case _ =>
    }
  }

  protected def getGutter: GutterIconRenderer = {
    val gutters = myFixture.findAllGutters()
    Assert.assertEquals("Wrong number of gutters", 1, gutters.size())
    gutters.get(0).asInstanceOf[GutterIconRenderer]
  }

  protected def createFile(text: String): Unit = myFixture.configureByText(s"$testClassName.scala", text)

  // because getElementAtCaret from fixture forces resolve and we don't want that
  protected def elementAtCaret = PsiTreeUtil.getParentOfType(myFixture.getFile.findElementAt(myFixture.getCaretOffset-1), classOf[ScalaPsiElement])
  protected def refAtCaret = elementAtCaret match {
    case ref: ScReferenceElement => ref
    case other: PsiElement => PsiTreeUtil.getParentOfType(other, classOf[ScReferenceElement])
  }
  protected def annotName: String = s"a_${getTestName(true)}".toLowerCase
  protected def testClassName: String = getTestName(false)
  protected def testClass: ScTypeDefinition =  myFixture.getFile.getChildren
    .flatMap(_.depthFirst().collectFirst{case c: ScTypeDefinition if c.name == testClassName => c})
    .headOption
    .getOrElse{Assert.fail(s"Class $testClassName not found"); throw new RuntimeException}
  protected val tq = "\"\"\""
}

object MetaAnnotationTestBase {

  private case class MetaParadiseLoader()(implicit val module: Module) extends MetaBaseLoader {
    override val name: String = "paradise"
    override val version: String = "3.0.0-M10" // FIXME version from buildinfo

    override protected def folder(implicit version: ScalaVersion): String =
      s"${name}_${version.minor}"

    override def path(implicit version: ScalaVersion): String = super.path

    override def init(implicit version: ScalaVersion): Unit = {}
  }

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


}
