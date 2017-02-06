package scala.meta.annotations

import java.io.File
import javax.swing.SwingUtilities

import com.intellij.ProjectTopics
import com.intellij.compiler.CompilerTestUtil
import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.compiler.{CompileContext, CompileStatusNotification, CompilerManager, CompilerMessageCategory}
import com.intellij.openapi.module.{JavaModuleType, Module}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{CompilerProjectExtension, ModuleRootAdapter, ModuleRootEvent, ModuleRootManager}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.testFramework.fixtures.{CodeInsightTestFixture, JavaCodeInsightFixtureTestCase}
import com.intellij.testFramework.{EdtTestUtil, PsiTestUtil, VfsTestUtil}
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.base.DisposableScalaLibraryLoader
import org.jetbrains.plugins.scala.base.libraryLoaders.CompositeLibrariesLoader
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.debugger.DebuggerTestUtil
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion
import org.jetbrains.plugins.scala.{TestFixtureProvider, extensions}
import org.junit.Assert

import scala.meta.ScalaMetaLibrariesOwner
import scala.meta.ScalaMetaLibrariesOwner.MetaBaseLoader

abstract class MetaAnnotationTestBase extends JavaCodeInsightFixtureTestCase with TestFixtureProvider with ScalaMetaLibrariesOwner {

  import MetaAnnotationTestBase._

  override def getFixture: CodeInsightTestFixture = myFixture

  private var deleteProjectAtTearDown = false

  override protected def getTestDataPath: String = TestUtils.getTestDataPath + "/scalameta"

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

    setUpCompiler(myModule)
  }

  def compileMetaSource(source: String = FileUtil.loadFile(new File(getTestDataPath, s"${getTestName(false)}.scala"))): List[String] = {
    implicit val project = getProject

    val root = extensions.inWriteAction {
      val baseDir = project.getBaseDir
      Option(baseDir.findChild("meta")).getOrElse(baseDir.createChildDirectory(null, "meta"))
    }

    implicit val metaModule = PsiTestUtil.addModule(project, JavaModuleType.getModuleType, "meta", root)
    implicit val version = scalaSdkVersion

    CompositeLibrariesLoader(
      new DisposableScalaLibraryLoader() +: additionalLibraries(metaModule)
    ).init

    enableParadisePlugin(metaModule)

    extensions.inWriteAction {
      val modifiableRootModel = ModuleRootManager.getInstance(myModule).getModifiableModel
      modifiableRootModel.addModuleOrderEntry(metaModule)
      modifiableRootModel.commit()
    }

    VfsTestUtil.createFile(root, "meta.scala", source)
    try {
      make()
    } finally {
      shutdownCompiler()
    }
  }

  def checkExpansionEquals(code: String, expectedExpansion: String): Unit = {
    myFixture.configureByText(s"Usage${getTestName(false)}.scala", code)
    val holder = ScalaPsiUtil.getParentOfType(myFixture.getElementAtCaret, classOf[ScAnnotationsHolder]).asInstanceOf[ScAnnotationsHolder]
    holder.getMetaExpansion match {
      case Right(tree) => Assert.assertEquals(expectedExpansion, tree.toString())
      case Left(reason) if reason.nonEmpty => Assert.fail(reason)
      case Left("") => Assert.fail("Expansion was empty - did annotation even run?")
    }
  }

  private def setUpCompiler(implicit module: Module): Unit = {
    CompilerTestUtil.enableExternalCompiler()
    DebuggerTestUtil.enableCompileServer(true)
    addRoots
    DebuggerTestUtil.forceJdk8ForBuildProcess()
  }

  private def shutdownCompiler(): Unit = {
    CompilerTestUtil.disableExternalCompiler(getProject)
    CompileServerLauncher.instance.stop()
  }

  private def addRoots(implicit module: Module) {
    inWriteAction {
      val srcRoot = getOrCreateChildDir("src")
      PsiTestUtil.addSourceRoot(module, srcRoot, false)
      val output = getOrCreateChildDir("out")

      CompilerProjectExtension.getInstance(getProject).setCompilerOutputUrl(output.getUrl)
    }
  }

  private def getOrCreateChildDir(name: String): VirtualFile = {
    val baseDir = getProject.getBaseDir
    Assert.assertNotNull(baseDir)

    val file = new File(baseDir.getCanonicalPath, name)
    if (!file.exists()) file.mkdir()

    LocalFileSystem.getInstance.refreshAndFindFileByPath(file.getCanonicalPath)
  }

  private def make(): List[String] = {
    val semaphore: Semaphore = new Semaphore
    semaphore.down()
    val callback = new ErrorReportingCallback(semaphore)
    EdtTestUtil.runInEdtAndWait(new ThrowableRunnable[Throwable] {
      def run() {
        CompilerTestUtil.saveApplicationSettings()
        saveProject()
        CompilerManager.getInstance(getProject).rebuild(callback)
      }
    })
    val maxCompileTime = 6000
    var i = 0
    while (!semaphore.waitFor(100) && i < maxCompileTime) {
      if (SwingUtilities.isEventDispatchThread) {
        UIUtil.dispatchAllInvocationEvents()
      }
      i += 1
    }
    Assert.assertTrue(s"Too long compilation of test data for ${getClass.getSimpleName}", i < maxCompileTime)
    if (callback.hasError) {
      deleteProjectAtTearDown = true
      callback.throwException()
    }
    callback.getMessages
  }

  private class ErrorReportingCallback(semaphore: Semaphore) extends CompileStatusNotification {
    private var myError: Option[Throwable] = None
    private var myMessages: List[String] = List.empty

    def finished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext): Unit = {
      try {
        myMessages = (for {
          category <- CompilerMessageCategory.values
          message <- compileContext.getMessages(category)
          msg = message.getMessage
          if category != CompilerMessageCategory.INFORMATION || !msg.startsWith("Compilation completed successfully")
        } yield
          category + ": " + msg).toList

        if (errors > 0) {
          Assert.fail("Compiler errors occurred! " + myMessages.mkString("\n"))
        }
        Assert.assertFalse("Code did not compile!", aborted)
      } catch {
        case t: Throwable => myError = Option(t)
      } finally {
        semaphore.up()
      }
    }

    def hasError: Boolean = myError != null

    def throwException() {
      myError.foreach(e => throw new RuntimeException(e))
    }

    def getMessages: List[String] = myMessages
  }

  private def saveProject(): Unit = {
    refreshVfs(getProject.getProjectFilePath)
    refreshVfs(myModule.getModuleFilePath)
    val applicationEx = ApplicationManagerEx.getApplicationEx
    val setting = applicationEx.isDoNotSave
    applicationEx.doNotSave(false)
    getProject.save()
    applicationEx.doNotSave(setting)
    CompilerTestUtil.saveApplicationSettings()
  }
}

object MetaAnnotationTestBase {

  private def enableParadisePlugin(module: Module)
                                  (implicit project: Project,
                                   version: ScalaSdkVersion): Unit = {
    val profile = ScalaCompilerConfiguration.instanceIn(project).defaultProfile
    val settings = profile.getSettings

    settings.plugins :+= MetaParadiseLoader()(module).path
    profile.setSettings(settings)
  }

  private case class MetaParadiseLoader(implicit val module: Module) extends MetaBaseLoader {
    override protected val name: String = "paradise"
    override protected val version: String = "3.0.0-M5"

    override protected def folder(implicit version: ScalaSdkVersion): String =
      s"${name}_${version.getMinor}"

    override def path(implicit version: ScalaSdkVersion): String = super.path

    override def init(implicit version: ScalaSdkVersion): Unit = {}
  }

  private def refreshVfs(path: String): Unit = {
    LocalFileSystem.getInstance.refreshAndFindFileByIoFile(new File(path)) match {
      case null =>
      case file => file.refresh(false, false)
    }
  }
}
