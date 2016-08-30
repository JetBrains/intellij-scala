package org.jetbrains.plugins.scala
package debugger

import java.io.File
import javax.swing.SwingUtilities

import com.intellij.ProjectTopics
import com.intellij.compiler.CompilerTestUtil
import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.compiler.{CompileContext, CompileStatusNotification, CompilerManager, CompilerMessageCategory}
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots._
import com.intellij.openapi.roots._
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs._
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.{EdtTestUtil, ModuleTestCase, PsiTestUtil, VfsTestUtil}
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.base.ScalaLibraryLoader
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert

import scala.collection.mutable.ListBuffer

/**
 * Nikolay.Tropin
 * 2/26/14
 */
abstract class ScalaCompilerTestBase extends ModuleTestCase with ScalaVersion {

  private var deleteProjectAtTearDown = false
  private var scalaLibraryLoader: ScalaLibraryLoader = _

  override def setUp(): Unit = {
    super.setUp()
    myProject.getMessageBus.connect(getTestRootDisposable).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter {
      override def rootsChanged(event: ModuleRootEvent) {
        forceFSRescan()
      }
    })
    CompilerTestUtil.enableExternalCompiler()

    addRoots()
    DebuggerTestUtil.enableCompileServer(useCompileServer)
    DebuggerTestUtil.forceJdk8ForBuildProcess()
  }

  protected def useCompileServer: Boolean = false

  protected def addRoots() {
    def getOrCreateChildDir(name: String) = {
      val file = new File(getBaseDir.getCanonicalPath, name)
      if (!file.exists()) file.mkdir()
      LocalFileSystem.getInstance.refreshAndFindFileByPath(file.getCanonicalPath)
    }

//  protected def addRoots() {

    inWriteAction {
      val srcRoot = getOrCreateChildDir("src")
      PsiTestUtil.addSourceRoot(getModule, srcRoot, false)
      val output = getOrCreateChildDir("out")
      CompilerProjectExtension.getInstance(getProject).setCompilerOutputUrl(output.getUrl)
    }
  }

  protected def addScalaSdk(loadReflect: Boolean = true) {
    scalaLibraryLoader = new ScalaLibraryLoader(getProject, getModule, getSourceRootDir.getCanonicalPath,
      loadReflect, Some(getTestProjectJdk))

    scalaLibraryLoader.loadScala(scalaSdkVersion)
  }

  protected def addIvyCacheLibrary(libraryName: String, libraryPath: String, jarNames: String*) {
    addIvyCacheLibraryToModule(myModule, libraryName, libraryPath, jarNames:_*)
  }

  protected def addIvyCacheLibraryToModule(module: Module, libraryName: String, libraryPath: String, jarNames: String*) = {
    val libsPath = TestUtils.getIvyCachePath
    val pathExtended = s"$libsPath/$libraryPath/"
    VfsRootAccess.allowRootAccess(pathExtended)
    PsiTestUtil.addLibrary(module, libraryName, pathExtended, jarNames: _*)
  }

  override protected def getTestProjectJdk: Sdk = {
//    val jdkTable = JavaAwareProjectJdkTableImpl.getInstanceEx
//    if (scalaVersion.startsWith("2.12")) {
//      DebuggerTestUtil.findJdk8()
//    }
//    else {
//      jdkTable.getInternalJdk
//    }
      DebuggerTestUtil.findJdk8()
  }

  protected def forceFSRescan() = BuildManager.getInstance.clearState(myProject)

  protected override def tearDown() {
    EdtTestUtil.runInEdtAndWait {
      new ThrowableRunnable[Throwable] {
        def run() {
          CompilerTestUtil.disableExternalCompiler(myProject)
          CompileServerLauncher.instance.stop()
          val baseDir = getBaseDir
          scalaLibraryLoader.clean()
          scalaLibraryLoader = null
          ScalaCompilerTestBase.super.tearDown()

          if (deleteProjectAtTearDown) VfsTestUtil.deleteFile(baseDir)
        }
      }
    }
  }

  protected def make(): List[String] = {
    val semaphore: Semaphore = new Semaphore
    semaphore.down()
    val callback = new ErrorReportingCallback(semaphore)
    EdtTestUtil.runInEdtAndWait(new ThrowableRunnable[Throwable] {
      def run() {
        CompilerTestUtil.saveApplicationSettings()
        val ioFile: File = VfsUtilCore.virtualToIoFile(myModule.getModuleFile)
        saveProject()
        assert(ioFile.exists, "File does not exist: " + ioFile.getPath)
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

    def hasError = myError != null

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

