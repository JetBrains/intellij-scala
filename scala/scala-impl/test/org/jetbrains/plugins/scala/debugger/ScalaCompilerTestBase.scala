package org.jetbrains.plugins.scala
package debugger

import java.io.File

import com.intellij.compiler.server.BuildManager
import com.intellij.compiler.{CompilerConfiguration, CompilerTestUtil}
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.compiler._
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots._
import com.intellij.openapi.roots._
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs._
import com.intellij.testFramework.{EdtTestUtil, ModuleTestCase, PsiTestUtil, VfsTestUtil}
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.ui.UIUtil
import javax.swing.SwingUtilities
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders._
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, ScalaCompileServerSettings}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.ProjectExt
import org.junit.Assert._

import scala.concurrent.duration
import scala.util.{Failure, Success, Try}

/**
 * Nikolay.Tropin
 * 2/26/14
 */
abstract class ScalaCompilerTestBase extends ModuleTestCase with ScalaSdkOwner {

  private var deleteProjectAtTearDown = false

  override def setUp(): Unit = {
    super.setUp()

    // uncomment to enable debugging of compile server in tests
    //    BuildManager.getInstance().setBuildProcessDebuggingEnabled(true)
    //    com.intellij.openapi.util.registry.Registry.get("compiler.process.debug.port").setValue(5006)

    myProject.subscribeToModuleRootChanged(getTestRootDisposable) { _ =>
      BuildManager.getInstance.clearState(myProject)
    }

    addRoots()
    compilerVmOptions.foreach(setCompilerVmOptions)
    DebuggerTestUtil.enableCompileServer(useCompileServer)
    DebuggerTestUtil.forceJdk8ForBuildProcess()
    setUpLibraries(myModule)
  }

  protected def compilerVmOptions: Option[String] = None

  protected def useCompileServer: Boolean = false

  protected def addRoots() {
    def getOrCreateChildDir(name: String) = {
      val file = new File(getBaseDir.getCanonicalPath, name)
      if (!file.exists()) file.mkdir()
      LocalFileSystem.getInstance.refreshAndFindFileByPath(file.getCanonicalPath)
    }

    inWriteAction {
      val srcRoot = getOrCreateChildDir("src")
      PsiTestUtil.addSourceRoot(getModule, srcRoot, false)
      val output = getOrCreateChildDir("out")
      CompilerProjectExtension.getInstance(getProject).setCompilerOutputUrl(output.getUrl)
    }
  }

  private def setCompilerVmOptions(options: String): Unit = {
    if (useCompileServer)
      ScalaCompileServerSettings.getInstance().COMPILE_SERVER_JVM_PARAMETERS = options
    else
      CompilerConfiguration.getInstance(getProject).setBuildProcessVMOptions(options)
  }

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(
    ScalaSDKLoader(includeScalaReflect = true),
    HeavyJDKLoader(),
    SourcesLoader(getSourceRootDir.getCanonicalPath)
  ) ++ additionalLibraries

  protected def additionalLibraries: Seq[LibraryLoader] = Seq.empty

  override protected def getTestProjectJdk: Sdk = SmartJDKLoader.getOrCreateJDK()

  protected override def tearDown(): Unit =
    EdtTestUtil.runInEdtAndWait { () =>
      val baseDir = getBaseDir
      try {
        CompilerTestUtil.disableExternalCompiler(myProject)
        ScalaCompilerTestBase.stopAndWait()
        disposeLibraries(myModule)

      } finally {
        ScalaCompilerTestBase.super.tearDown()
        if (deleteProjectAtTearDown) VfsTestUtil.deleteFile(baseDir)
      }
    }

  protected final def make(): Seq[String] = {
    val semaphore = new Semaphore
    semaphore.down()

    val callback = new ErrorReportingCallback(semaphore)
    EdtTestUtil.runInEdtAndWait(() => {
      CompilerTestUtil.saveApplicationSettings()
      val ioFile = VfsUtilCore.virtualToIoFile(myModule.getModuleFile)
      saveProject()
      assertTrue("File does not exist: " + ioFile.getPath, ioFile.exists)
      CompilerManager.getInstance(getProject).rebuild(callback)
    })

    val maxCompileTime = 6000
    var i = 0
    while (!semaphore.waitFor(100) && i < maxCompileTime) {
      if (SwingUtilities.isEventDispatchThread) {
        UIUtil.dispatchAllInvocationEvents()
      }
      i += 1
    }

    assertTrue(
      s"Too long compilation of test data for ${getClass.getSimpleName}.test${getTestName(false)}",
      i < maxCompileTime
    )

    callback.result match {
      case Success(messages) =>
        messages
      case Failure(throwable) =>
        deleteProjectAtTearDown = true
        throw new RuntimeException(throwable)
    }
  }

  private class ErrorReportingCallback(semaphore: Semaphore) extends CompileStatusNotification {

    private var result_ : Try[Seq[String]] = _

    def result: Try[Seq[String]] = result_

    def finished(aborted: Boolean,
                 errors: Int,
                 warnings: Int,
                 compileContext: CompileContext): Unit =
      try {
        val messages = for {
          category <- CompilerMessageCategory.values
          compilerMessage <- compileContext.getMessages(category)

          message = compilerMessage.getMessage
          if !(category == CompilerMessageCategory.INFORMATION && message.startsWith("Compilation completed successfully"))
        } yield category + ": " + message

        result_ = Success(messages)

        errors match {
          case 0 => assertFalse("Code did not compile!", aborted)
          case _ => fail("Compiler errors occurred! " + messages.mkString("\n"))
        }
      } catch {
        case throwable: Throwable => result_ = Failure(throwable)
      } finally {
        semaphore.up()
      }
  }

  protected def getBaseDir: VirtualFile = {
    val baseDir = myProject.baseDir
    assertNotNull(baseDir)
    baseDir
  }

  protected def addFileToProject(relativePath: String, text: String): Unit = VfsTestUtil.createFile(
    getSourceRootDir,
    relativePath,
    StringUtil.convertLineSeparators(text)
  )

  private def getSourceRootDir: VirtualFile = getBaseDir.findChild("src")

  private def saveProject(): Unit = {
    val applicationEx = ApplicationManagerEx.getApplicationEx
    val setting = applicationEx.isSaveAllowed
    applicationEx.setSaveAllowed(true)
    getProject.save()
    applicationEx.setSaveAllowed(setting)
  }
}

object ScalaCompilerTestBase {

  import duration.{Duration, DurationInt}

  def stopAndWait(timeout: Duration = 10.seconds): Unit = assertTrue(
    "Compile server process have not terminated after " + timeout,
    CompileServerLauncher.stopAndWaitTermination(timeout.toMillis)
  )
}