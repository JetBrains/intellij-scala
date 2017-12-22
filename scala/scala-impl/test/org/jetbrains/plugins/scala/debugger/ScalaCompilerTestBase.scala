package org.jetbrains.plugins.scala
package debugger

import java.io.File
import javax.swing.SwingUtilities

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.DurationInt

import com.intellij.ProjectTopics
import com.intellij.compiler.server.BuildManager
import com.intellij.compiler.{CompilerConfiguration, CompilerTestUtil}
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.compiler.{CompileContext, CompileStatusNotification, CompilerManager, CompilerMessageCategory}
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots._
import com.intellij.openapi.roots._
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs._
import com.intellij.testFramework.{EdtTestUtil, ModuleTestCase, PsiTestUtil, VfsTestUtil}
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.base.libraryLoaders._
import org.jetbrains.plugins.scala.compiler.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.util.CompileServerUtil
import org.junit.Assert

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

    myProject.getMessageBus
      .connect(getTestRootDisposable)
      .subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener {
        override def rootsChanged(event: ModuleRootEvent) {
          forceFSRescan()
        }
      })

    addRoots()
    compilerVmOptions.foreach(setCompilerVmOptions)
    DebuggerTestUtil.enableCompileServer(useCompileServer)
    DebuggerTestUtil.forceJdk8ForBuildProcess()
    setUpLibraries()
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

  override implicit protected def module: Module = getModule

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(
    ScalaSDKLoader(includeScalaReflect = true),
    HeavyJDKLoader(),
    SourcesLoader(getSourceRootDir.getCanonicalPath)
  ) ++ additionalLibraries

  protected def additionalLibraries: Seq[LibraryLoader] = Seq.empty

  override protected def getTestProjectJdk: Sdk = SmartJDKLoader.getOrCreateJDK()

  protected def forceFSRescan(): Unit = BuildManager.getInstance.clearState(myProject)

  protected override def tearDown(): Unit =
    EdtTestUtil.runInEdtAndWait { () =>
      val baseDir = getBaseDir
      try {
        CompilerTestUtil.disableExternalCompiler(myProject)
        CompileServerUtil.stopAndWait(10.seconds)
        disposeLibraries()

      } finally {
        ScalaCompilerTestBase.super.tearDown()
        if (deleteProjectAtTearDown) VfsTestUtil.deleteFile(baseDir)
      }
  }

  protected def make(): List[String] = {
    val semaphore: Semaphore = new Semaphore
    semaphore.down()
    val callback = new ErrorReportingCallback(semaphore)
    EdtTestUtil.runInEdtAndWait(() => {
      CompilerTestUtil.saveApplicationSettings()
      val ioFile: File = VfsUtilCore.virtualToIoFile(myModule.getModuleFile)
      saveProject()
      assert(ioFile.exists, "File does not exist: " + ioFile.getPath)
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
    Assert.assertTrue(s"Too long compilation of test data for ${getClass.getSimpleName}.test${getTestName(false)}", i < maxCompileTime)
    if (callback.hasError) {
      deleteProjectAtTearDown = true
      callback.throwException()
    }
    callback.getMessages
  }

  private class ErrorReportingCallback(semaphore: Semaphore) extends CompileStatusNotification {
    private var myError: Throwable = _
    private val myMessages = ListBuffer[String]()

    def finished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext) {
      try {
        for (category <- CompilerMessageCategory.values) {
          for (message <- compileContext.getMessages(category)) {
            val msg: String = message.getMessage
            if (category != CompilerMessageCategory.INFORMATION || !msg.startsWith("Compilation completed successfully")) {
              myMessages += (category + ": " + msg)
            }
          }
        }
        if (errors > 0) {
          Assert.fail("Compiler errors occurred! " + myMessages.mkString("\n"))
        }
        Assert.assertFalse("Code did not compile!", aborted)
      }
      catch {
        case t: Throwable => myError = t
      }
      finally {
        semaphore.up()
      }
    }

    def hasError: Boolean = myError != null

    def throwException() {
      if (myError != null) throw new RuntimeException(myError)
    }

    def getMessages: List[String] = myMessages.toList
  }

  protected def getBaseDir: VirtualFile = {
    val baseDir: VirtualFile = myProject.getBaseDir
    Assert.assertNotNull(baseDir)
    baseDir
  }

  protected def addFileToProject(relativePath: String, text: String) {
    VfsTestUtil.createFile(getSourceRootDir, relativePath, StringUtil.convertLineSeparators(text))
  }

  protected def getSourceRootDir: VirtualFile = {
    getBaseDir.findChild("src")
  }

  protected def saveProject(): Unit = {
    val applicationEx = ApplicationManagerEx.getApplicationEx
    val setting = applicationEx.isDoNotSave
    applicationEx.doNotSave(false)
    getProject.save()
    applicationEx.doNotSave(setting)
  }
}

