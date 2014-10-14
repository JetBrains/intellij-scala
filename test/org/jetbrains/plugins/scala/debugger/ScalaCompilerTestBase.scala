package org.jetbrains.plugins.scala
package debugger

import com.intellij.testFramework.{PsiTestUtil, VfsTestUtil, ModuleTestCase}
import com.intellij.ProjectTopics
import com.intellij.compiler.CompilerTestUtil
import com.intellij.openapi.roots._
import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import com.intellij.packaging.artifacts.ArtifactManager
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.ui.UIUtil
import com.intellij.openapi.compiler.{CompileContext, CompilerMessageCategory, CompileStatusNotification, CompilerManager}
import javax.swing.SwingUtilities
import scala.collection.mutable.ListBuffer
import junit.framework.Assert
import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}
import com.intellij.openapi.projectRoots._
import org.jetbrains.plugins.scala.util.TestUtils
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import extensions._
import project._

/**
 * Nikolay.Tropin
 * 2/26/14
 */
abstract class ScalaCompilerTestBase extends ModuleTestCase {

  protected def useExternalCompiler: Boolean = true

  override def setUp(): Unit = {
    super.setUp()
    if (useExternalCompiler) {
      myProject.getMessageBus.connect(myTestRootDisposable).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter {
        override def rootsChanged(event: ModuleRootEvent) {
          forceFSRescan()
        }
      })
      CompilerTestUtil.enableExternalCompiler(myProject)
    }
    else CompilerTestUtil.disableExternalCompiler(myProject)

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

  protected def addScalaSdk() {
    ScalaLoader.loadScala()
    val cl = SyntheticClasses.get(getProject)
    if (!cl.isClassesRegistered) cl.registerClasses()
    val root = TestUtils.getTestDataPath.replace("\\", "/") + "/scala-compiler/"
    PsiTestUtil.addLibrary(myModule, "scala-compiler", root, "scala-compiler.jar", "scala-library.jar")
    myModule.libraries.find(_.getName == "scala-compiler").foreach { library =>
      val compilerClasspath = Seq(new File(root, "scala-compiler.jar"), new File(root, "scala-library.jar"))
      library.convertToScalaSdkWith(ScalaLanguageLevel.getDefault, compilerClasspath)
    }
  }

  override protected def getTestProjectJdk: Sdk = JavaAwareProjectJdkTableImpl.getInstanceEx.getInternalJdk

  protected def forceFSRescan() = BuildManager.getInstance.clearState(myProject)

  protected override def tearDown() {
    if (useExternalCompiler) CompilerTestUtil.disableExternalCompiler(myProject)

    super.tearDown()
  }

  protected def make(): List[String] = {
    val semaphore: Semaphore = new Semaphore
    semaphore.down()
    val callback = new ErrorReportingCallback(semaphore)
    UIUtil.invokeAndWaitIfNeeded(new Runnable {
      def run() {
        try {
          if (useExternalCompiler) {
            getProject.save()
            CompilerTestUtil.saveApplicationSettings()
            val ioFile: File = VfsUtilCore.virtualToIoFile(myModule.getModuleFile)
            if (!ioFile.exists) {
              getProject.save()
              assert(ioFile.exists, "File does not exist: " + ioFile.getPath)
            }
          }
          CompilerManager.getInstance(getProject).rebuild(callback)
        }
        catch {
          case e: Exception => throw new RuntimeException(e)
        }
      }
    })
    while (!semaphore.waitFor(100)) {
      if (SwingUtilities.isEventDispatchThread) {
        UIUtil.dispatchAllInvocationEvents()
      }
    }
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

