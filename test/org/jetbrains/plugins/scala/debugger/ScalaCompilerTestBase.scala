package org.jetbrains.plugins.scala
package debugger

import java.io.File
import javax.swing.SwingUtilities

import com.intellij.ProjectTopics
import com.intellij.compiler.CompilerTestUtil
import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.{CompileContext, CompileStatusNotification, CompilerManager, CompilerMessageCategory}
import com.intellij.openapi.projectRoots._
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots._
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}
import com.intellij.testFramework.{ModuleTestCase, PsiTestUtil, VfsTestUtil}
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.ui.UIUtil
import junit.framework.Assert
import org.jetbrains.plugins.scala.compiler.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.template.Artifact
import org.jetbrains.plugins.scala.util.TestUtils

import scala.collection.mutable.ListBuffer

/**
 * Nikolay.Tropin
 * 2/26/14
 */
abstract class ScalaCompilerTestBase extends ModuleTestCase with ScalaVersion {

  protected def useExternalCompiler: Boolean = true

  override def setUp(): Unit = {
    VfsRootAccess.SHOULD_PERFORM_ACCESS_CHECK = false
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

  protected def addScalaSdk(loadReflect: Boolean = true) {
    ScalaLoader.loadScala()
    val cl = SyntheticClasses.get(getProject)
    if (!cl.isClassesRegistered) cl.registerClasses()

    val root = TestUtils.getTestDataPath.replace("\\", "/") + "/scala-compiler/" +
            (if (scalaVersion != "") scalaVersion + "/" else "")
    
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

  def discoverJDK18() = {
    import java.io._
    def isJDK(f: File) = f.listFiles().exists { b =>
      b.getName == "bin" && b.listFiles().exists(x => x.getName == "javac.exe" || x.getName == "javac")
    }
    def inJvm(path: String, suffix: String, postfix: String = "") = {
      Option(new File(path))
        .filter(_.exists())
        .flatMap(_.listFiles()
          .sortBy(_.getName)
          .reverse
          .find(f => f.getName.contains(suffix) && isJDK(new File(f, postfix)))
          .map(new File(_, s"$postfix/jre").getAbsolutePath)
        )
    }
    val candidates = Seq(
      Option(sys.env.getOrElse("JDK_18", sys.env.getOrElse("JDK_18_x64", null))),  // teamcity style
      inJvm("/usr/lib/jvm", "1.8"),                   // oracle style
      inJvm("/usr/lib/jvm", "-8"),                    // openjdk style
      inJvm("C:\\Program Files\\Java\\", "1.8"),      // oracle windows style
      inJvm("C:\\Program Files (x86)\\Java\\", "1.8"),      // oracle windows style
      inJvm("/Library/Java/JavaVirtualMachines", "1.8", "/Contents/Home")// mac style
    )
    candidates.flatten.headOption
  }

  override protected def getTestProjectJdk: Sdk = {
    val jdkTable = JavaAwareProjectJdkTableImpl.getInstanceEx

    if (scalaVersion.startsWith("2.12")) {
      val mockJdk8Name = "mock java 1.8"

      def addMockJdk8(): Sdk = {
        val pathDefault = TestUtils.getTestDataPath.replace("\\", "/") + "/mockJDK1.8/jre"
        val path = discoverJDK18().getOrElse(pathDefault)
        val jdk = JavaSdk.getInstance.createJdk(mockJdk8Name, path)
        val oldJdk = jdkTable.findJdk(mockJdk8Name)
        inWriteAction {
          if (oldJdk != null) jdkTable.removeJdk(oldJdk)
          jdkTable.addJdk(jdk)
        }
        jdk
      }
      addMockJdk8()
    }
    else {
      jdkTable.getInternalJdk
    }
  }

  protected def forceFSRescan() = BuildManager.getInstance.clearState(myProject)

  protected override def tearDown() {
    if (useExternalCompiler) CompilerTestUtil.disableExternalCompiler(myProject)

    super.tearDown()
  }

  protected def make(): List[String] = {
    ScalaCompileServerSettings.getInstance().COMPILE_SERVER_ENABLED = false
    ApplicationManager.getApplication.saveSettings()
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

