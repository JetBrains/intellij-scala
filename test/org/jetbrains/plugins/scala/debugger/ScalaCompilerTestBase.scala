package org.jetbrains.plugins.scala
package debugger

import java.io.File
import javax.swing.SwingUtilities

import com.intellij.ProjectTopics
import com.intellij.compiler.CompilerTestUtil
import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.compiler.{CompileContext, CompileStatusNotification, CompilerManager, CompilerMessageCategory}
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots._
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots._
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}
import com.intellij.testFramework.{ModuleTestCase, PsiTestUtil, VfsTestUtil}
import com.intellij.util.Processor
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.ui.UIUtil
import junit.framework.Assert
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.template.Artifact
import org.jetbrains.plugins.scala.util.TestUtils

import scala.collection.immutable.HashSet
import scala.collection.mutable.ListBuffer

/**
 * Nikolay.Tropin
 * 2/26/14
 */
abstract class ScalaCompilerTestBase extends ModuleTestCase {

  protected var compilerVersion: Option[String] = null

  protected def useExternalCompiler: Boolean = true

  override def setUp(): Unit = {
    super.setUp()
    if (useExternalCompiler) {
      myProject.getMessageBus.connect(myTestRootDisposable).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter {
        override def rootsChanged(event: ModuleRootEvent) {
          forceFSRescan()
        }
      })
      CompilerTestUtil.enableExternalCompiler()
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

  protected val compilerDirectorySuffix = ""
  
  protected def addScalaSdk(loadReflect: Boolean = true) {
    ScalaLoader.loadScala()
    val cl = SyntheticClasses.get(getProject)
    if (!cl.isClassesRegistered) cl.registerClasses()

    val root = TestUtils.getTestDataPath.replace("\\", "/") + "/scala-compiler/" +
            (if (compilerDirectorySuffix != "") compilerDirectorySuffix + "/" else "")
    
    VfsRootAccess.allowRootAccess(root)

    PsiTestUtil.addLibrary(myModule, "scala-compiler", root, "scala-library.jar")

    myModule.libraries.find(_.getName == "scala-compiler").foreach { library =>
      val compilerClasspath = Seq("scala-compiler.jar", "scala-library.jar") ++
              (if (loadReflect) Seq("scala-reflect.jar") else Seq.empty)

      val languageLevel = Artifact.ScalaCompiler.versionOf(new File(root, "scala-compiler.jar"))
              .flatMap(ScalaLanguageLevel.from).getOrElse(ScalaLanguageLevel.Default)

      inWriteAction {
        library.convertToScalaSdkWith(languageLevel, compilerClasspath.map(new File(root, _)))
      }
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

