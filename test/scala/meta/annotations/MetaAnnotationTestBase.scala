package scala.meta.annotations

import java.io.File
import java.security.MessageDigest
import javax.xml.bind.DatatypeConverter

import com.intellij.ProjectTopics
import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.module.{JavaModuleType, Module}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ModuleRootAdapter, ModuleRootEvent}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.{PsiTestUtil, VfsTestUtil}
import org.apache.commons.io.filefilter.SuffixFileFilter
import org.jetbrains.plugins.scala.base.DisposableScalaLibraryLoader
import org.jetbrains.plugins.scala.base.libraryLoaders.LibraryLoader
import org.jetbrains.plugins.scala.debugger.ScalaVersion
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, inWriteAction}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert

import scala.meta.ScalaMetaLibrariesOwner.MetaBaseLoader
import scala.meta.{Compilable, ScalaMetaLibrariesOwner}

abstract class MetaAnnotationTestBase extends JavaCodeInsightFixtureTestCase with ScalaMetaLibrariesOwner with Compilable {

  import MetaAnnotationTestBase._

  override implicit lazy val project: Project = getProject
  override def rootProject = getProject
  override def rootModule = myModule

  private lazy val metaDirectory = inWriteAction {
    val baseDir = project.getBaseDir
    baseDir.findChild("meta") match {
      case null => baseDir.createChildDirectory(null, "meta")
      case directory => directory
    }
  }

  override implicit lazy val module: Module = PsiTestUtil.addModule(project, JavaModuleType.getModuleType, "meta", metaDirectory)

  override protected def getTestDataPath: String = TestUtils.getTestDataPath + "/scalameta"

  override def librariesLoaders: Seq[LibraryLoader] =
    Seq(new DisposableScalaLibraryLoader()) ++ additionalLibraries

  override def setUp(): Unit = {
    super.setUp()

    val project = getProject
    project.getMessageBus
      .connect(getTestRootDisposable)
      .subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter {
        override def rootsChanged(event: ModuleRootEvent) {
          BuildManager.getInstance.clearState(project)
        }
      })

    addRoots(module)
    addRoots(myModule)
    setUpLibraries()

    inWriteAction {
      val modifiableRootModel = myModule.modifiableModel
      modifiableRootModel.addModuleOrderEntry(module)
      modifiableRootModel.commit()
    }
  }

  private def tryLoadFromCache(module: Module, hash: String): Boolean = {
    val cacheRoot = testCacheRoot(hash)
    if (cacheRoot.list(new SuffixFileFilter(".class")).nonEmpty) {
      val outputDir = new File(CompilerPaths.getModuleOutputPath(module, false))
      outputDir.mkdirs()
      FileUtil.copyDirContent(cacheRoot, outputDir)
      val timestamp = System.currentTimeMillis()
      outputDir.listFiles().foreach(_.setLastModified(timestamp)) // to avoid out-of-date class errors
      refreshVfs(outputDir.getAbsolutePath)
      true
    } else false
  }

  private def saveToCache(module: Module, hash: String): Unit = {
    val testRoot = testCacheRoot(hash)
    FileUtil.copyDirContent(new File(CompilerPaths.getModuleOutputPath(module, false)), testRoot)
  }

  private def testCacheRoot(hash: String) = {
    val cacheRoot = new File(sys.props("user.home"), ".cache/IJ_scala_tests_cache/")
    val testRoot = new File(cacheRoot, s"$hash/")
    testRoot.mkdirs()
    testRoot
  }

  protected def compileMetaSource(source: String = FileUtil.loadFile(new File(getTestDataPath, s"${getTestName(false)}.scala"))): List[String] = {
    addMetaSource(source)
    val md5 = MessageDigest.getInstance("MD5")
    md5.update(source.getBytes)
    md5.update(version.major.getBytes)
    md5.update(ScalaMetaLibrariesOwner.metaVersion.getBytes)
    val hashStr = DatatypeConverter.printHexBinary(md5.digest())
    if (!tryLoadFromCache(module, hashStr)) {
      setUpCompiler(module)
      enableParadisePlugin()
      val messages = runMake()
      saveToCache(module, hashStr)
      messages
    } else List.empty
  }

  protected def addMetaSource(source: String = FileUtil.loadFile(new File(getTestDataPath, s"${getTestName(false)}.scala"))): Unit = {
    VfsTestUtil.createFile(metaDirectory, "meta.scala", source)
  }

  protected def enableParadisePlugin(): Unit = {
    val profile = ScalaCompilerConfiguration.instanceIn(project).defaultProfile
    val settings = profile.getSettings
    settings.plugins :+= MetaParadiseLoader()(module).path
    profile.setSettings(settings)
  }


  protected def checkExpansionEquals(code: String, expectedExpansion: String): Unit = {
    import scala.meta.intellij.psiExt._
    myFixture.configureByText(s"Usage${getTestName(false)}.scala", code)
    val holder = ScalaPsiUtil.getParentOfType(myFixture.getElementAtCaret, classOf[ScAnnotationsHolder]).asInstanceOf[ScAnnotationsHolder]
    holder.getMetaExpansion match {
      case Right(tree) => Assert.assertEquals(expectedExpansion, tree.toString())
      case Left(reason) if reason.nonEmpty => Assert.fail(reason)
      case Left("") => Assert.fail("Expansion was empty - did annotation even run?")
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
  protected def annotName: String = s"a_${getTestName(true)}".toLowerCase
  protected def testClassName: String = getTestName(false)
  protected def testClass: ScTypeDefinition =  myFixture.getFile.getChildren
    .flatMap(_.depthFirst().collectFirst{case c: ScTypeDefinition if c.name == testClassName => c})
    .headOption
    .getOrElse{Assert.fail(s"Class $testClassName not found"); throw new RuntimeException}
  protected val tq = "\"\"\""
}

object MetaAnnotationTestBase {

  private case class MetaParadiseLoader(implicit val module: Module) extends MetaBaseLoader {
    override protected val name: String = "paradise"
    override protected val version: String = "3.0.0-M8"

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
