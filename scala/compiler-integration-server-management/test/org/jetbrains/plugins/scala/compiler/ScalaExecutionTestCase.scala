package org.jetbrains.plugins.scala.compiler

import com.intellij.debugger.impl.OutputChecker
import com.intellij.execution.ExecutionTestCase
import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.{EdtTestUtil, IndexingTestUtil, PlatformTestUtil, StartupActivityTestUtil}
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.libraryLoaders.{HeavyJDKLoader, LibraryLoader, ScalaSDKLoader, SmartJDKLoader}
import org.jetbrains.plugins.scala.base.{ScalaSdkOwner, SourceRootTestUtil}
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.compiler.testUtils.CompileServerTestUtil
import org.jetbrains.plugins.scala.extensions.{PathExt, inWriteAction}
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.util.TestUtils

import java.io.{ObjectInputStream, ObjectOutputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import scala.annotation.nowarn
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.{Try, Using}

/**
 * Adds support for dynamically adding source files and compiling them to bytecode before the execution of a test
 * which runs Java programs (debugger tests and testing support tests).
 */
trait ScalaExecutionTestCase extends ExecutionTestCase with ScalaSdkOwner {

  private val Log: Logger = Logger.getInstance(getClass)

  protected def testDataDirectoryName: String

  private def testDataPath: Path = Path.of(TestUtils.getTestDataPath, testDataDirectoryName)

  private def versionSpecific: Path = Path.of(s"scala-${version.minor}")

  // example:
  // ./community/scala/scala-impl/testdata/testingSupport/ScalaTestWithJunitRunnerTest/scala-2.13.17
  private def testAppPath: Path = testDataPath.resolve(getClass.getSimpleName).resolve(versionSpecific)

  private def appOutputPath: Path = Path.of(s"${testAppPath}_out")

  protected def srcPath: Path = testAppPath.resolve("src")

  protected def classFilesOutputPath: Path = appOutputPath.resolve("classes")

  private def checksumsPath: Path = appOutputPath.resolve("checksums")

  private def checksumsFilePath: Path = checksumsPath.resolve("checksums.dat")

  private val sourceFiles: mutable.Map[String, String] = mutable.Map.empty

  override protected def initOutputChecker(): OutputChecker =
    new OutputChecker(() => getTestAppPath, () => getAppOutputPath) {
      override def checkValid(jdk: Sdk, sortClassPath: Boolean): Unit = {}
    }

  override protected def getTestAppPath: String = testAppPath.toString

  protected def includeScalaLibrarySources: Boolean = false

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(
    ScalaSDKLoader(includeScalaReflectIntoCompilerClasspath = true, includeScalaLibrarySources = includeScalaLibrarySources),
    HeavyJDKLoader(testProjectJdkVersion)
  ) ++ additionalLibraries

  protected def additionalLibraries: Seq[LibraryLoader] = Seq.empty

  override protected def getModuleOutputDir: Path = classFilesOutputPath

  override protected def getAppOutputPath: String = getModuleOutputDir.toString

  override def testProjectJdkVersion: LanguageLevel = LanguageLevel.JDK_17

  override protected def getProjectLanguageLevel: LanguageLevel = testProjectJdkVersion

  override protected def getTestProjectJdk: Sdk = SmartJDKLoader.getOrCreateJDK(testProjectJdkVersion)

  /**
   * When `true`, #setUp will generate source files, start the compile server, and compile the project.
   * Override to `false` for tests that use a static, pre-built test project (e.g., run via sbt shell).
   */
  protected def usesManagedSourcesAndCompilation: Boolean = true

  protected def reuseCompileServerProcessBetweenTests: Boolean = true

  override protected def setUpProject(): Unit = {
    super.setUpProject()
    inWriteAction(ProjectRootManager.getInstance(getProject).setProjectSdk(getTestProjectJdk))
  }

  override protected def setUpModule(): Unit = {
    super.setUpModule()
    EdtTestUtil.runInEdtAndWait { () =>
      SourceRootTestUtil.addSourceRoot(getModule, srcPath)
      setUpLibraries(getModule)
    }
  }

  override protected def setUp(): Unit = {
    if (usesManagedSourcesAndCompilation) {
      // Make sure that the src dir is clean before run, to avoid collisions with stale files from a previous
      // test data state (e.g. renamed or deleted source files).
      // Note, we could do that in the end of the test in "tearDown",
      // but it might just be helpful to leave them in place to inspect the created sources after local test execution
      NioFiles.deleteRecursively(srcPath)
      Files.createDirectories(srcPath)
      sourceFiles.foreach { case (filePath, fileContents) =>
        ensureFileExistsAndHasContent(filePath, fileContents)
      }

      // Only clean the output dir when the previously compiled classes cannot be reused for the freshly written
      // sources (in that case `compileProject` skips the rebuild). All test methods of a test class share the same
      // sources, so the test project is compiled once per test class instead of once per test method. The decision
      // must be made BEFORE anything under `appOutputPath` is deleted, otherwise the checksums are always missing
      // and every test method triggers a full rebuild.
      if (!compiledOutputIsUpToDate) {
        NioFiles.deleteRecursively(appOutputPath)
      }

      // `classFilesOutputPath` must exist before `super.setUp()`: `ExecutionTestCase#setUp` runs its own
      // unconditional `compileProject()` when the module output dir does not exist.
      Files.createDirectories(classFilesOutputPath)
      Files.createDirectories(checksumsPath)
    }

    super.setUp()

    if (usesManagedSourcesAndCompilation) {
      if (reuseCompileServerProcessBetweenTests) {
        //noinspection ApiStatus,UnstableApiUsage
        CompileServerTestUtil.registerLongRunningThreads()
      } else {
        // We don't want to reuse the compile server in this test class, but it may have already been started.
        // We should shut it down first.
        CompileServerLauncher.stopServerAndWait()
      }

      LocalFileSystem.getInstance().refreshNioFiles(srcPath.children().asJava)
      compileProject()
    }
  }

  private def ensureFileExistsAndHasContent(relativePath: String, fileContent: String): Unit = {
    val absolutePath = srcPath.resolve(relativePath)
    if (!absolutePath.exists || Files.readString(absolutePath) != fileContent) {
      Files.createDirectories(absolutePath.getParent)
      val bytes = fileContent.getBytes(StandardCharsets.UTF_8)
      Files.write(absolutePath, bytes)
    }
  }

  override protected def tearDown(): Unit = {
    try {
      if (!reuseCompileServerProcessBetweenTests) {
        CompileServerLauncher.stopServerAndWait()
      }
      EdtTestUtil.runInEdtAndWait { () =>
        // Drain the async tail of the test run (post-startup activities are never joined in unit test mode, the
        // execution UI is populated via invokeLater after the run has already finished) while the project is still
        // alive, so that lazily created project services register and dispose under a healthy container. The
        // compilation step used to absorb all of this by pumping the EDT for the duration of the whole build.
        // See also the platform precedent in DaemonAnalyzerTestCase.tearDown (IJPL-840).
        StartupActivityTestUtil.waitForProjectActivitiesToComplete(getProject): @nowarn("cat=deprecation")
        IndexingTestUtil.waitUntilIndexesAreReady(getProject)
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        disposeLibraries(getModule)
      }
    } finally {
      super.tearDown()
    }
  }

  override protected def compileProject(): Unit = {
    if (compiledOutputIsUpToDate) {
      val message = s"Skipping project compilation: checksums are the same ($testAppPath)"
      Log.info(message)
      System.out.println(s"##teamcity[message text='$message' status='NORMAL']")
      // The CompilerTester used by the compilation branch runs this barrier in its constructor;
      // keep the two branches equivalent (the VFS refresh in setUp schedules scanning work).
      IndexingTestUtil.waitUntilIndexesAreReady(getProject)
    } else {
      super.compileProject()
      writeChecksumsToDisk(calculateSrcChecksums())
    }
  }

  /**
   * The compiled classes can be reused iff the compiled output is not empty and the checksums of the current
   * sources are exactly the ones recorded after the last successful compilation.
   */
  private def compiledOutputIsUpToDate: Boolean =
    classFilesOutputPath.isDirectory && classFilesOutputPath.children().nonEmpty &&
      Try(checksumsMatch(calculateSrcChecksums(), loadChecksumsFromDisk())).getOrElse(false)

  private def checksumsMatch(srcChecksums: Map[Path, Array[Byte]], diskChecksums: Map[Path, Array[Byte]]): Boolean =
    // Exact equality of the file sets: a source file that was removed since the last compilation must also
    // trigger a rebuild, so that no stale class files remain in the output dir.
    srcChecksums.keySet == diskChecksums.keySet &&
      srcChecksums.forall { case (path, sum) => diskChecksums.get(path).exists(java.util.Arrays.equals(sum, _)) }

  private def loadChecksumsFromDisk(): Map[Path, Array[Byte]] =
    Using(new ObjectInputStream(Files.newInputStream(checksumsFilePath)))(_.readObject())
      .map(_.asInstanceOf[Map[String, Array[Byte]]])
      .map(_.map { case (path, checksum) => (Path.of(path), checksum) })
      .getOrElse(Map.empty)

  private def calculateSrcChecksums(): Map[Path, Array[Byte]] = {
    val messageDigest = MessageDigest.getInstance("MD5")

    def checksum(file: Path): Array[Byte] = {
      val fileBytes = Files.readAllBytes(file)
      messageDigest.digest(fileBytes)
    }

    def checksumsInDir(dir: Path): Seq[(Path, Array[Byte])] =
      dir.children().flatMap { f =>
        if (f.isDirectory) checksumsInDir(f) else Seq((f, checksum(f)))
      }

    checksumsInDir(srcPath).toMap
  }

  private def writeChecksumsToDisk(checksums: Map[Path, Array[Byte]]): Unit = {
    val strings = checksums.map { case (path, sum) => (path.toString, sum) }
    Using(new ObjectOutputStream(Files.newOutputStream(checksumsFilePath)))(_.writeObject(strings))
  }

  override protected def createJavaParameters(mainClass: String): JavaParameters = {
    val params = new JavaParameters()
    params.getClassPath.addAll(getModule.scalaCompilerClasspath.map(_.toCanonicalPath.toString).asJava)
    params.getClassPath.add(getAppOutputPath)
    params.setJdk(getTestProjectJdk)
    params.setWorkingDirectory(getTestAppPath)
    params.setMainClass(mainClass)
    params
  }

  protected def addSourceFile(relativePath: String, contents: String): Unit = {
    sourceFiles.update(relativePath, contents)
  }

  protected def addSourceFileImmediately(relativePath: String, contents: String): Unit = {
    ensureFileExistsAndHasContent(relativePath, contents)
  }

  protected def addScalaSourceFile(relativePath: String, @Language("Scala") contents: String): Unit = {
    this.addSourceFile(relativePath, contents)
  }

  protected def addScalaSourceFileImmediately(relativePath: String, @Language("Scala") contents: String): Unit = {
    this.addSourceFileImmediately(relativePath, contents)
  }

  protected def assertEquals[A, B](expected: A, actual: B)(implicit ev: A <:< B): Unit = {
    org.junit.Assert.assertEquals(expected, actual)
  }

  protected def assertEquals[A, B](message: String, expected: A, actual: B)(implicit ev: A <:< B): Unit = {
    org.junit.Assert.assertEquals(message, expected, actual)
  }
}
