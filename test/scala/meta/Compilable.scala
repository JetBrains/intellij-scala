package scala.meta

import java.io.File
import javax.swing.SwingUtilities

import com.intellij.compiler.CompilerTestUtil
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.compiler.{CompileContext, CompileStatusNotification, CompilerManager, CompilerMessageCategory}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.CompilerProjectExtension
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.testFramework.{EdtTestUtil, PsiTestUtil}
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.debugger.DebuggerTestUtil
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.junit.Assert


trait Compilable {

  private var deleteProjectAtTearDown = false

  def rootProject: Project
  def rootModule: Module

  protected def refreshVfs(path: String): Unit = {
    LocalFileSystem.getInstance.refreshAndFindFileByIoFile(new File(path)) match {
      case null =>
      case file => file.refresh(false, false)
    }
  }

  protected def runMake(): List[String] = {
    try {
      make()
    } finally {
      shutdownCompiler()
    }
  }
  protected def setUpCompiler(implicit module: Module): Unit = {
    CompilerTestUtil.enableExternalCompiler()
    DebuggerTestUtil.enableCompileServer(true)
    DebuggerTestUtil.forceJdk8ForBuildProcess()
  }

  protected def shutdownCompiler(): Unit = {
    CompilerTestUtil.disableExternalCompiler(rootProject)
    CompileServerLauncher.instance.stop()
  }

  protected def addRoots(module: Module) {
    inWriteAction {
      val srcRoot = getOrCreateChildDir("src")
      PsiTestUtil.addSourceRoot(module, srcRoot, false)
      val output = getOrCreateChildDir("out")

      CompilerProjectExtension.getInstance(rootProject).setCompilerOutputUrl(output.getUrl)
    }
  }

  protected def getOrCreateChildDir(name: String): VirtualFile = {
    val baseDir = rootProject.getBaseDir
    Assert.assertNotNull(baseDir)

    val file = new File(baseDir.getCanonicalPath, name)
    if (!file.exists()) file.mkdir()

    LocalFileSystem.getInstance.refreshAndFindFileByPath(file.getCanonicalPath)
  }

  protected def make(): List[String] = {
    val semaphore: Semaphore = new Semaphore
    semaphore.down()
    val callback = new ErrorReportingCallback(semaphore)
    EdtTestUtil.runInEdtAndWait(new ThrowableRunnable[Throwable] {
      def run() {
        CompilerTestUtil.saveApplicationSettings()
        saveProject()
        CompilerManager.getInstance(rootProject).rebuild(callback)
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

  protected class ErrorReportingCallback(semaphore: Semaphore) extends CompileStatusNotification {
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

  protected def saveProject(): Unit = {
    refreshVfs(rootProject.getProjectFilePath)
    refreshVfs(rootModule.getModuleFilePath)
    val applicationEx = ApplicationManagerEx.getApplicationEx
    val setting = applicationEx.isDoNotSave
    applicationEx.doNotSave(false)
    rootProject.save()
    applicationEx.doNotSave(setting)
    CompilerTestUtil.saveApplicationSettings()
  }
}
