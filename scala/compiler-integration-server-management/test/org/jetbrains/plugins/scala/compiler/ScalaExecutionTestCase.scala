package org.jetbrains.plugins.scala.compiler

import com.intellij.debugger.impl.OutputChecker
import com.intellij.execution.ExecutionTestCase
import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.EdtTestUtil
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
      // Make sure that the src and output dirs are clean before run, to avoid and collisions between previous test data state
      // Note, we could do that in the end of the test in "tearDown",
      // but it might just be helpful to leave them in place to inspect the created sources after local test execution
      NioFiles.deleteRecursively(srcPath)
      NioFiles.deleteRecursively(appOutputPath)

      Files.createDirectories(srcPath)
      Files.createDirectories(classFilesOutputPath)
      Files.createDirectories(checksumsPath)

      sourceFiles.foreach { case (filePath, fileContents) =>
        ensureFileExistsAndHasContent(filePath, fileContents)
      }
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
        disposeLibraries(getModule)
      }
    } finally {
      super.tearDown()
    }
  }

  override protected def compileProject(): Unit = {
    def loadChecksumsFromDisk(): Map[Path, Array[Byte]] =
      Using(new ObjectInputStream(Files.newInputStream(checksumsFilePath)))(_.readObject())
        .map(_.asInstanceOf[Map[String, Array[Byte]]])
        .map(_.map { case (path, checksum) => (Path.of(path), checksum) })
        .getOrElse(Map.empty)

    val messageDigest = MessageDigest.getInstance("MD5")

    def calculateSrcCheksums(): Map[Path, Array[Byte]] = {
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

    def shouldCompile(srcChecksums: Map[Path, Array[Byte]], diskChecksums: Map[Path, Array[Byte]]): Boolean = {
      val checksumsAreSame = srcChecksums.forall { case (srcPath, srcSum) =>
        diskChecksums.get(srcPath).exists(java.util.Arrays.equals(srcSum, _))
      }
      !checksumsAreSame
    }

    def writeChecksumsToDisk(checksums: Map[Path, Array[Byte]]): Unit = {
      val strings = checksums.map { case (path, sum) => (path.toString, sum) }
      Using(new ObjectOutputStream(Files.newOutputStream(checksumsFilePath)))(_.writeObject(strings))
    }

    val srcChecksums = calculateSrcCheksums()

    val compareChecksums = for {
      diskChecksums <- Try(loadChecksumsFromDisk())
    } yield shouldCompile(srcChecksums, diskChecksums)

    val needsCompilation = compareChecksums.getOrElse(true)

    if (needsCompilation) {
      super.compileProject()
      writeChecksumsToDisk(srcChecksums)
    } else {
      val message = s"Skipping project compilation: checksums are the same ($testAppPath)"
      Log.info(message)
      System.out.println(s"##teamcity[message text='$message' status='NORMAL']")
    }
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
