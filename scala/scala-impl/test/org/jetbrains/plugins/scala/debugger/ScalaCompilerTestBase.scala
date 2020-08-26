package org.jetbrains.plugins.scala
package debugger

import java.io.File
import java.util.{List => JList}

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.compiler._
import com.intellij.openapi.projectRoots._
import com.intellij.openapi.roots._
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs._
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework._
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders._
import org.jetbrains.plugins.scala.compilation.CompilerTestUtil
import org.jetbrains.plugins.scala.compilation.CompilerTestUtil.{NoOpRevertableChange, RevertableChange}
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, ScalaCompileServerSettings}
import org.jetbrains.plugins.scala.debugger.ScalaCompilerTestBase.{ListCompilerMessageExt, markCompileServerThreadsLongRunning}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.project.{IncrementalityType, ProjectExt}
import org.jetbrains.plugins.scala.util.UnloadAwareDisposable
import org.junit.Assert
import org.junit.Assert._

import scala.jdk.CollectionConverters._
import scala.concurrent.duration
import scala.language.implicitConversions

/**
 * Nikolay.Tropin
 * 2/26/14
 */
abstract class ScalaCompilerTestBase extends JavaModuleTestCase with ScalaSdkOwner {

  private var compilerTester: CompilerTester = _

  private var revertable: RevertableChange = NoOpRevertableChange

  /**
   * Called on each project, but before initializing ThreadWatcher.
   * Needed to avoid ThreadLeaked exceptions after each test run,
   * cause we want compile server to be reused in all tests.
   */
  override def setUpProject(): Unit = {
    super.setUpProject()

    if (useCompileServer && reuseCompileServerProcessBetweenTests) {
      markCompileServerThreadsLongRunning()
    }

    revertable =
      CompilerTestUtil.withEnabledCompileServer(useCompileServer) |+|
        CompilerTestUtil.withCompileServerJdk(compileServerJdk) |+|
        CompilerTestUtil.withForcedJdkForBuildProcess(buildProcessJdk)
    revertable.apply()
  }

  override protected def setUp(): Unit = {
    super.setUp()

    // uncomment to enable debugging of compile server in tests
    //    BuildManager.getInstance().setBuildProcessDebuggingEnabled(true)
    //    com.intellij.openapi.util.registry.Registry.get("compiler.process.debug.port").setValue(5006)

    myProject.subscribeToModuleRootChanged(getTestRootDisposable) { _ =>
      BuildManager.getInstance.clearState(myProject)
    }

    addSrcRoot()
    compilerVmOptions.foreach(setCompilerVmOptions)

    setUpLibraries(getModule)
    ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType = incrementalityType
    compilerTester = new CompilerTester(getModule)
    // !!! MUST BE CALLED AFTER compilerTester new CompilerTester(...)
    //     new CompilerTester resets modules SDK to internal SDK ! (ノಠ益ಠ)ノ彡┻━┻ so we need to setup jdk again
    setUpJdk()
    addOutRoot()
  }

  override protected def tearDown(): Unit = try {
    compilerTester.tearDown()
    if (!reuseCompileServerProcessBetweenTests) {
      ScalaCompilerTestBase.stopAndWait()
    } else {
      //  server will be stopped when Application shuts down (see ShutDownTracker in CompileServerLauncher)
    }
    EdtTestUtil.runInEdtAndWait { () =>
      disposeLibraries(getModule)
    }
  } finally {
    compilerTester = null
    revertable.revert()
    EdtTestUtil.runInEdtAndWait { () =>
      ScalaCompilerTestBase.super.tearDown()
    }
  }

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(
    ScalaSDKLoader(includeScalaReflect = true),
    HeavyJDKLoader(testProjectJdkVersion),
    SourcesLoader(getSourceRootDir.getCanonicalPath)
  ) ++ additionalLibraries

  override def defaultJdkVersion: LanguageLevel =
    if (version < LatestScalaVersions.Scala_2_11) LanguageLevel.JDK_1_8
    else super.defaultJdkVersion

  override protected def getTestProjectJdk: Sdk = SmartJDKLoader.getOrCreateJDK(testProjectJdkVersion)

  protected def compileServerJdk: Sdk = getTestProjectJdk

  protected def buildProcessJdk: Sdk = getTestProjectJdk

  protected def additionalLibraries: Seq[LibraryLoader] = Seq.empty

  protected def incrementalityType: IncrementalityType = IncrementalityType.IDEA

  protected def compilerVmOptions: Option[String] = None

  protected def useCompileServer: Boolean = false
  protected def reuseCompileServerProcessBetweenTests: Boolean = false

  protected def compiler: CompilerTester = compilerTester

  protected def getBaseDir: VirtualFile = {
    val baseDir = myProject.baseDir
    assertNotNull(baseDir)
    baseDir
  }

  protected def getSourceRootDir: VirtualFile = getBaseDir.findChild("src")

  protected def addFileToProjectSources(relativePath: String, text: String): VirtualFile = VfsTestUtil.createFile(
    getSourceRootDir,
    relativePath,
    StringUtil.convertLineSeparators(text)
  )

  private def getOrCreateChildDir(name: String) = {
    val file = new File(getBaseDir.getCanonicalPath, name)
    if (!file.exists()) file.mkdir()
    LocalFileSystem.getInstance.refreshAndFindFileByPath(file.getCanonicalPath)
  }

  private def addSrcRoot(): Unit = inWriteAction {
    val srcRoot = getOrCreateChildDir("src")
    PsiTestUtil.addSourceRoot(getModule, srcRoot, false)
  }


  private def addOutRoot(): Unit = inWriteAction {
    val outRoot = getOrCreateChildDir("out")
    CompilerProjectExtension.getInstance(getProject).setCompilerOutputUrl(outRoot.getUrl)
  }

  private def setCompilerVmOptions(options: String): Unit =
    if (useCompileServer) {
      ScalaCompileServerSettings.getInstance.COMPILE_SERVER_JVM_PARAMETERS = options
    } else {
      CompilerConfiguration.getInstance(getProject).setBuildProcessVMOptions(options)
    }

  protected implicit def listCompilerMessage2Ext(messages: JList[CompilerMessage]): ListCompilerMessageExt =
    new ListCompilerMessageExt(messages)
}

object ScalaCompilerTestBase {

  import duration.{Duration, DurationInt}

  // TODO: review if needed?
  def stopAndWait(timeout: Duration = 10.seconds): Unit = assertTrue(
    s"Compile server process have not terminated after $timeout",
    CompileServerLauncher.stop(timeout.toMillis)
  )

  private def markCompileServerThreadsLongRunning(): Unit = {
    ThreadTracker.longRunningThreadCreated(
      UnloadAwareDisposable.scalaPluginDisposable,
      "scalaCompileServer",
      "BaseDataReader: output stream of scalaCompileServer",
      "BaseDataReader: error stream of scalaCompileServer"
    )
  }

  implicit class ListCompilerMessageExt(val messages: JList[CompilerMessage])
    extends AnyVal {

    /**
     * Checks if no compilation problems.
     *
     * @param allowWarnings if ''true'' checks only ERROR-messages, else ERROR- and WARNING-messages.
     */
    def assertNoProblems(allowWarnings: Boolean = false): Unit = {
      val categories = if (allowWarnings)
        Set(CompilerMessageCategory.ERROR)
      else
        Set(CompilerMessageCategory.ERROR, CompilerMessageCategory.WARNING)

      val problems = messages.asScala.filter { message =>
        categories.contains(message.getCategory)
      }.toSeq
      if (problems.nonEmpty) {
        val otherMessages = messages.asScala.filterNot(problems.contains)
        Assert.fail(
          s"""No compiler errors expected, but got:
            |${messagesText(problems)}
            |Other compiler messages:
            |${messagesText(otherMessages)}""".stripMargin
        )
      }
    }

    private def messagesText(messages: collection.Seq[CompilerMessage]): String =
      messages.zipWithIndex.map { case (message, idx) => s"[$idx] [${message.getCategory}] : ${message.getMessage.trim}"}.mkString("\n")
  }
}