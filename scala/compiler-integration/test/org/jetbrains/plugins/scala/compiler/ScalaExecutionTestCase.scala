package org.jetbrains.plugins.scala.compiler

import com.intellij.debugger.impl.OutputChecker
import com.intellij.execution.ExecutionTestCase
import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.EdtTestUtil
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{HeavyJDKLoader, LibraryLoader, ScalaSDKLoader, SmartJDKLoader, SourcesLoader}
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.util.TestUtils

import java.io.{File, FileInputStream, FileOutputStream, ObjectInputStream, ObjectOutputStream}
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

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(
    ScalaSDKLoader(includeScalaReflectIntoCompilerClasspath = true),
    HeavyJDKLoader(testProjectJdkVersion),
    SourcesLoader(srcPath.toString)
  ) ++ additionalLibraries

  protected def additionalLibraries: Seq[LibraryLoader] = Seq.empty

  override protected def getModuleOutputDir: Path = classFilesOutputPath

  override protected def getAppOutputPath: String = getModuleOutputDir.toString

  override def testProjectJdkVersion: LanguageLevel = LanguageLevel.JDK_17

  override protected def getProjectLanguageLevel: LanguageLevel = testProjectJdkVersion

  override protected def getTestProjectJdk: Sdk = SmartJDKLoader.getOrCreateJDK(testProjectJdkVersion)

  override protected def setUpModule(): Unit = {
    super.setUpModule()
    EdtTestUtil.runInEdtAndWait { () =>
      setUpLibraries(getModule)
    }
  }

  override protected def setUp(): Unit = {
    TestUtils.optimizeSearchingForIndexableFiles()

    Files.createDirectories(srcPath)
    Files.createDirectories(classFilesOutputPath)
    Files.createDirectories(checksumsPath)

    sourceFiles.foreach { case (filePath, fileContents) =>
      val path = srcPath.resolve(filePath)
      if (!path.toFile.exists() || Files.readString(path) != fileContents) {
        Files.createDirectories(path.getParent)
        val bytes = fileContents.getBytes(StandardCharsets.UTF_8)
        Files.write(path, bytes)
      }
    }

    super.setUp()

    LocalFileSystem.getInstance().refreshIoFiles(srcPath.toFile.listFiles().toList.asJava)
    compileProject()
  }

  override protected def tearDown(): Unit = {
    try {
      CompileServerLauncher.stopServerAndWait()
      EdtTestUtil.runInEdtAndWait { () =>
        disposeLibraries(getModule)
      }
    } finally {
      super.tearDown()
    }
  }

  override protected def compileProject(): Unit = {
    def loadChecksumsFromDisk(): Map[Path, Array[Byte]] =
      Using(new ObjectInputStream(new FileInputStream(checksumsFilePath.toFile)))(_.readObject())
        .map(_.asInstanceOf[Map[String, Array[Byte]]])
        .map(_.map { case (path, checksum) => (Path.of(path), checksum) })
        .getOrElse(Map.empty)

    val messageDigest = MessageDigest.getInstance("MD5")

    def calculateSrcCheksums(): Map[Path, Array[Byte]] = {
      def checksum(file: File): Array[Byte] = {
        val fileBytes = Files.readAllBytes(file.toPath)
        messageDigest.digest(fileBytes)
      }

      def checksumsInDir(dir: File): List[(Path, Array[Byte])] =
        dir.listFiles().toList.flatMap { f =>
          if (f.isDirectory) checksumsInDir(f) else List((f.toPath, checksum(f)))
        }

      checksumsInDir(srcPath.toFile).toMap
    }

    def shouldCompile(srcChecksums: Map[Path, Array[Byte]], diskChecksums: Map[Path, Array[Byte]]): Boolean = {
      val checksumsAreSame = srcChecksums.forall { case (srcPath, srcSum) =>
        diskChecksums.get(srcPath).exists(java.util.Arrays.equals(srcSum, _))
      }
      !checksumsAreSame
    }

    def writeChecksumsToDisk(checksums: Map[Path, Array[Byte]]): Unit = {
      val strings = checksums.map { case (path, sum) => (path.toString, sum) }
      Using(new ObjectOutputStream(new FileOutputStream(checksumsFilePath.toFile)))(_.writeObject(strings))
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
    params.getClassPath.addAllFiles(getModule.scalaCompilerClasspath.toArray)
    params.getClassPath.add(getAppOutputPath)
    params.setJdk(getTestProjectJdk)
    params.setWorkingDirectory(getTestAppPath)
    params.setMainClass(mainClass)
    params
  }

  protected def addSourceFile(path: String, contents: String): Unit = {
    sourceFiles.update(path, contents)
  }

  protected def assertEquals[A, B](expected: A, actual: B)(implicit ev: A <:< B): Unit = {
    org.junit.Assert.assertEquals(expected, actual)
  }

  protected def assertEquals[A, B](message: String, expected: A, actual: B)(implicit ev: A <:< B): Unit = {
    org.junit.Assert.assertEquals(message, expected, actual)
  }
}
