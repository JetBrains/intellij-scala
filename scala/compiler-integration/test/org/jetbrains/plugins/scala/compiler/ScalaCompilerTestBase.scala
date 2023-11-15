package org.jetbrains.plugins.scala
package compiler

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.compiler._
import com.intellij.openapi.projectRoots._
import com.intellij.openapi.roots._
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs._
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework._
import org.jetbrains.plugins.scala.util.TestUtils
//noinspection ApiStatus
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders._
import org.jetbrains.plugins.scala.compiler.ScalaCompilerTestBase.ListCompilerMessageExt
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.util.{CompilerTestUtil, RevertableChange}
import org.junit.Assert
import org.junit.Assert._

import java.io.File
import java.util.{List => JList}
import scala.collection.mutable
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.jdk.CollectionConverters._
import scala.language.implicitConversions

abstract class ScalaCompilerTestBase extends JavaModuleTestCase with ScalaSdkOwner {

  private var compilerTester: CompilerTester = _

  private val createdFiles: mutable.Set[VirtualFile] = mutable.Set.empty

  /**
   * Called on each project, but before initializing ThreadWatcher.
   * Needed to avoid ThreadLeaked exceptions after each test run,
   * cause we want compile server to be reused in all tests.
   */
  override def setUpProject(): Unit = {
    super.setUpProject()

    val revertable =
      CompilerTestUtil.withEnabledCompileServer(useCompileServer) |+|
        CompilerTestUtil.withCompileServerJdk(compileServerJdk) |+|
        CompilerTestUtil.withForcedJdkForBuildProcess(buildProcessJdk) |+|
        RevertableChange.withApplicationSettingsSaving
    revertable.applyChange(getTestRootDisposable)
  }

  override protected def setUp(): Unit = {
    TestUtils.optimizeSearchingForIndexableFiles()

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
    compilerTester = new CompilerTester(myProject, java.util.List.of(getModule), null, false)
    // We need to enforce calculating of the versionString value to ensure presence of it in the JPS-process (╥﹏╥)
    getTestProjectJdk.getVersionString
    addOutRoot()
  }

  override protected def tearDown(): Unit = try {
    compilerTester.tearDown()
    if (!reuseCompileServerProcessBetweenTests) {
      compileServerShutdownTimeout match {
        case _: Duration.Infinite => CompileServerLauncher.stopServerAndWait()
        case duration: FiniteDuration => CompileServerLauncher.stopServerAndWaitFor(duration)
      }
    } else {
      //  server will be stopped when Application shuts down (see ShutDownTracker in CompileServerLauncher)
    }
    EdtTestUtil.runInEdtAndWait { () =>
      disposeLibraries(getModule)
    }
  } finally {
    createdFiles.foreach(VfsTestUtil.deleteFile)
    compilerTester = null
    EdtTestUtil.runInEdtAndWait { () =>
      ScalaCompilerTestBase.super.tearDown()
    }
  }

  protected val includeReflectLibrary: Boolean = true
  protected val includeCompilerAsLibrary: Boolean = false

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(
    ScalaSDKLoader(includeReflectLibrary, includeCompilerAsLibrary),
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

  protected def incrementalityType: IncrementalityType = IncrementalityType.SBT

  protected def compilerVmOptions: Option[String] = None

  //TODO: set to "true" by default, because it reflects the default behaviour of IDEA
  // (review extended classes and set to "false" where needed)
  protected def useCompileServer: Boolean = false
  protected def reuseCompileServerProcessBetweenTests: Boolean = false
  protected def compileServerShutdownTimeout: Duration = Duration.Inf

  protected def compiler: CompilerTester = compilerTester

  protected def getBaseDir: VirtualFile = {
    val baseDir = PlatformTestUtil.getOrCreateProjectBaseDir(myProject)
    assertNotNull(baseDir)
    baseDir
  }

  protected def getSourceRootDir: VirtualFile = getBaseDir.findChild("src")

  protected def addFileToProjectSources(relativePath: String, text: String): VirtualFile = {
    val file = VfsTestUtil.createFile(getSourceRootDir, relativePath, StringUtil.convertLineSeparators(text))
    createdFiles += file
    file
  }

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

      val problems: Seq[CompilerMessage] = messages.asScala.filter { message =>
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

    private def messagesText(messages: collection.Seq[CompilerMessage]): String = {
      val fileToMessages = messages.groupBy(_.getVirtualFile)
      fileToMessages
        .toSeq
        .sortBy(_._1.toString)
        .map { case (file, messages) =>
          val messagesConcatenated = messages
            .map { message => s"${message.getCategory}: ${message.getMessage.trim}" }
            .mkString("\n")
          s"$file:\n$messagesConcatenated"
        }
        .mkString("\n")
    }
  }
}
