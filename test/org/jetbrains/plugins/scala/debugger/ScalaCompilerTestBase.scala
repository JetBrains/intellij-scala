package org.jetbrains.plugins.scala
package debugger

import java.io.File
import javax.swing.SwingUtilities

import com.intellij.ProjectTopics
import com.intellij.compiler.CompilerTestUtil
import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.compiler.{CompileContext, CompileStatusNotification, CompilerManager, CompilerMessageCategory}
import com.intellij.openapi.projectRoots._
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots._
import com.intellij.openapi.vfs._
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.{PsiTestUtil, VfsTestUtil}
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.ui.UIUtil
import junit.framework.Assert
import org.jetbrains.plugins.scala.base.ScalaLibraryLoader
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses

import scala.collection.mutable.ListBuffer

/**
 * Nikolay.Tropin
 * 2/26/14
 */
abstract class ScalaCompilerTestBase extends CompileServerTestBase with ScalaVersion {

  override def setUp(): Unit = {
    VfsRootAccess.SHOULD_PERFORM_ACCESS_CHECK = false

    super.setUp()
    myProject.getMessageBus.connect(myTestRootDisposable).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter {
      override def rootsChanged(event: ModuleRootEvent) {
        forceFSRescan()
      }
    })
    CompilerTestUtil.enableExternalCompiler()

    addRoots()
  }

  protected def addRoots() {
    inWriteAction {
      val srcRoot = getBaseDir.findChild("src").toOption.getOrElse(
        getBaseDir.createChildDirectory(this, "src")
      )
      PsiTestUtil.addSourceRoot(getModule, srcRoot, false)
      val output = getBaseDir.findChild("out").toOption.getOrElse(
        getBaseDir.createChildDirectory(this, "out")
      )
      CompilerProjectExtension.getInstance(getProject).setCompilerOutputUrl(output.getUrl)
    }
  }

  protected def addScalaSdk(loadReflect: Boolean = true) {
    ScalaLoader.loadScala()
    val cl = SyntheticClasses.get(getProject)
    if (!cl.isClassesRegistered) cl.registerClasses()

    ScalaLibraryLoader.addScalaSdk(myModule, scalaSdkVersion, loadReflect)
  }

  override protected def getTestProjectJdk: Sdk = {
    val jdkTable = JavaAwareProjectJdkTableImpl.getInstanceEx
    if (scalaVersion.startsWith("2.12")) {
      DebuggerTestUtil.findJdk8()
    }
    else {
      jdkTable.getInternalJdk
    }
  }

  protected def forceFSRescan() = BuildManager.getInstance.clearState(myProject)

  protected override def tearDown() {
    CompilerTestUtil.disableExternalCompiler(myProject)

    super.tearDown()
  }

  protected def make(): List[String] = {
    DebuggerTestUtil.findJdk8()
    DebuggerTestUtil.setCompileServerSettings()

    val semaphore: Semaphore = new Semaphore
    semaphore.down()
    val callback = new ErrorReportingCallback(semaphore)
    UIUtil.invokeAndWaitIfNeeded(new Runnable {
      def run() {
        try {
          getProject.save()
          CompilerTestUtil.saveApplicationSettings()
          val ioFile: File = VfsUtilCore.virtualToIoFile(myModule.getModuleFile)
          if (!ioFile.exists) {
            getProject.save()
            assert(ioFile.exists, "File does not exist: " + ioFile.getPath)
          }
          CompilerManager.getInstance(getProject).rebuild(callback)
        }
        catch {
          case e: Exception => throw new RuntimeException(e)
        }
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
    callback.throwException()
    callback.getMessages
  }

  private class ErrorReportingCallback(semaphore: Semaphore) extends CompileStatusNotification {
    private var myError: Throwable = null
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
    VfsTestUtil.createFile(getSourceRootDir, relativePath, text)
  }

  protected def getSourceRootDir: VirtualFile = {
    getBaseDir.findChild("src")
  }
}

