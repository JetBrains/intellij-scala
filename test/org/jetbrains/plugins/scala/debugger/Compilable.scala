package org.jetbrains.plugins.scala.debugger

import java.io.File
import javax.swing.SwingUtilities

import com.intellij.ProjectTopics
import com.intellij.compiler.CompilerTestUtil
import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.compiler.{CompileContext, CompileStatusNotification, CompilerManager, CompilerMessageCategory}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{CompilerProjectExtension, ModuleRootAdapter, ModuleRootEvent}
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.testFramework.{EdtTestUtil, PsiTestUtil}
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.extensions._
import org.junit.Assert

import scala.collection.mutable.ListBuffer

/**
  * @author Mikhail Mutcianko
  * @since 04.08.16
  */


trait Compilable {

  def getCompileableProject: Project
  def getMainModule: Module
  def getRootDisposable: Disposable
  def getTestName: String

  private var deleteProjectAtTearDown = false

  protected def setUpCompler(): Unit = {
    getCompileableProject.getMessageBus.connect(getRootDisposable).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter {
      override def rootsChanged(event: ModuleRootEvent) {
        forceFSRescan()
      }
    })
    CompilerTestUtil.enableExternalCompiler()
    addRoots()
    DebuggerTestUtil.forceJdk8ForBuildProcess()
  }

  protected def shutdownCompiler() = {
    CompilerTestUtil.disableExternalCompiler(getCompileableProject)
    CompileServerLauncher.instance.stop()
  }

  protected def addRoots() {
    inWriteAction {
      val srcRoot = getOrCreateChildDir("src")
      PsiTestUtil.addSourceRoot(getMainModule, srcRoot, false)
      val output = getOrCreateChildDir("out")
      CompilerProjectExtension.getInstance(getCompileableProject).setCompilerOutputUrl(output.getUrl)
    }
  }

  def getOrCreateChildDir(name: String) = {
    val file = new File(getBaseDir.getCanonicalPath, name)
    if (!file.exists()) file.mkdir()
    LocalFileSystem.getInstance.refreshAndFindFileByPath(file.getCanonicalPath)
  }

  protected def getBaseDir: VirtualFile = {
    val baseDir: VirtualFile = getCompileableProject.getBaseDir
    Assert.assertNotNull(baseDir)
    baseDir
  }

  protected def forceFSRescan() = BuildManager.getInstance.clearState(getCompileableProject)

  protected def make(): List[String] = {
    val semaphore: Semaphore = new Semaphore
    semaphore.down()
    val callback = new ErrorReportingCallback(semaphore)
    EdtTestUtil.runInEdtAndWait(new ThrowableRunnable[Throwable] {
      def run() {
        CompilerTestUtil.saveApplicationSettings()
//        val ioFile: File = VfsUtilCore.virtualToIoFile(getMainModule.getModuleFile)
        saveProject()
//        assert(ioFile.exists, "File does not exist: " + ioFile.getPath)
        CompilerManager.getInstance(getCompileableProject).rebuild(callback)
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

    def hasError = myError != null

    def throwException() {
      if (myError != null) throw new RuntimeException(myError)
    }

    def getMessages: List[String] = myMessages.toList
  }

  protected def saveProject(): Unit = {
    val applicationEx = ApplicationManagerEx.getApplicationEx
    val setting = applicationEx.isDoNotSave
    applicationEx.doNotSave(false)
    getCompileableProject.save()
    applicationEx.doNotSave(setting)
  }


}
