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
import org.jetbrains.plugins.scala.DependencyManagerBase.MavenResolver
import org.jetbrains.plugins.scala.base.SourceRootTestUtil
import org.jetbrains.plugins.scala.util.CompilerTestUtil.{applyEnabledCompileServerSettings, withModifiedCompileServerSettings}

import java.nio.file.{Files, Path}
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

    val modifiedCompileServerSettings = withModifiedCompileServerSettings { settings =>
      applyEnabledCompileServerSettings(settings, useCompileServer)
      // required for setting up the compile server JDK
      settings.USE_DEFAULT_SDK = false
      settings.COMPILE_SERVER_SDK = compileServerJdk.getName
    }
    val revertable = modifiedCompileServerSettings |+| CompilerTestUtil.withForcedJdkForBuildProcess(buildProcessJdk)
    revertable.applyChange(getTestRootDisposable)

    // uncomment to enable debugging of compile server in tests
    //    BuildManager.getInstance().setBuildProcessDebuggingEnabled(true)
    //    com.intellij.openapi.util.registry.Registry.get("compiler.process.debug.port").setValue(5006)

    myProject.subscribeToModuleRootChanged(getTestRootDisposable) { _ =>
      BuildManager.getInstance.clearState(myProject)
    }

    addSrcRoot()
    compilerVmOptions.foreach(setCompilerVmOptions)

    SourceRootTestUtil.addSourceRoot(getModule, getSourceRootDir.toNioPath)
    setUpLibraries(getModule)
    ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType = incrementalityType
    compilerTester = new CompilerTester(myProject, java.util.List.of(getModule), null, false)
    // We need to enforce calculating of the versionString value to ensure presence of it in the JPS-process (╥﹏╥)
    getTestProjectJdk.getVersionString
    addOutRoot()
  }

  override protected def setUp(): Unit = {
    super.setUp()
    if (reuseCompileServerProcessBetweenTests) {
      CompileServerTestUtil.registerLongRunningThreads()
    } else {
      // We don't want to reuse the compile server in this test class, but it may have already been started.
      // We should shut it down first.
      stopCompileServer()
    }
  }

  override protected def tearDown(): Unit = try {
    compilerTester.tearDown()
    if (!reuseCompileServerProcessBetweenTests) {
      stopCompileServer()
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
  protected def compilerBridgeBinaryJar: Option[Path] = None

  private object DependencyManagerWithNightly extends DependencyManagerBase {
    override protected def resolvers: Seq[DependencyManagerBase.Resolver] =
      super.resolvers :+ MavenResolver("scala-maven-nightlies", "https://repo.scala-lang.org/artifactory/maven-nightlies")
  }

  protected def useDependencyManagerWithNightlies: Boolean = false

  private val dependencyManager: DependencyManagerBase =
    if (useDependencyManagerWithNightlies) DependencyManagerWithNightly
    else DependencyManager

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(
    ScalaSDKLoader(
      includeScalaReflectIntoCompilerClasspath = includeReflectLibrary,
      includeScalaCompilerIntoLibraryClasspath = includeCompilerAsLibrary,
      compilerBridgeBinaryJar = compilerBridgeBinaryJar,
      dependencyManager = dependencyManager
    ),
    HeavyJDKLoader(testProjectJdkVersion)
  ) ++ additionalLibraries

  override def defaultJdkVersion: LanguageLevel =
    if (version < LatestScalaVersions.Scala_2_11) LanguageLevel.JDK_1_8
    else super.defaultJdkVersion

  override protected def getTestProjectJdk: Sdk = SmartJDKLoader.getOrCreateJDK(testProjectJdkVersion)

  protected def compileServerJdk: Sdk = getTestProjectJdk

  protected def buildProcessJdk: Sdk = CompileServerLauncher.defaultSdk(getProject)

  protected def additionalLibraries: Seq[LibraryLoader] = Seq.empty

  protected def incrementalityType: IncrementalityType = IncrementalityType.SBT

  protected def compilerVmOptions: Option[String] = None

  //TODO: set to "true" by default, because it reflects the default behaviour of IDEA
  // (review extended classes and set to "false" where needed)
  protected def useCompileServer: Boolean = true
  protected def reuseCompileServerProcessBetweenTests: Boolean = true
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

  private def getOrCreateChildDir(name: String): VirtualFile = {
    val dir = getBaseDir.toNioPath.resolve(name)
    if (!Files.exists(dir)) Files.createDirectory(dir)
    LocalFileSystem.getInstance.refreshAndFindFileByNioFile(dir)
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

  private def stopCompileServer(): Unit = {
    compileServerShutdownTimeout match {
      case _: Duration.Infinite => CompileServerLauncher.stopServerAndWait()
      case duration: FiniteDuration => CompileServerLauncher.stopServerAndWaitFor(duration)
    }
  }

  protected def findClassFile(name: String): Path = {
    val cls = compiler.findClassFile(name, getModule)
    assertNotNull(s"Could not find compiled class file $name", cls)
    cls.toPath
  }

  protected def removeFile(path: Path): Unit = {
    val virtualFile = VfsUtil.findFile(path, true)
    inWriteAction {
      virtualFile.delete(null)
    }
  }
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
        .sortBy(t => Option(t._1).fold("")(_.toString))
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
