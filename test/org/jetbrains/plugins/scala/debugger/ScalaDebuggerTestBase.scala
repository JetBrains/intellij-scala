package org.jetbrains.plugins.scala
package debugger

import scala.collection.mutable
import com.intellij.testFramework.{PsiTestUtil, PlatformTestCase, UsefulTestCase}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.config.{LibraryLevel, LibraryId, ScalaFacet}
import java.io._
import com.intellij.ide.highlighter.{ModuleFileType, ProjectFileType}
import java.nio.file.{FileSystems, Path, Paths, Files}
import com.intellij.execution.runners.{ExecutionEnvironmentBuilder, ProgramRunner}
import com.intellij.execution.process.{ProcessHandler, ProcessListener}
import com.intellij.openapi.module.Module
import com.intellij.execution.Executor
import com.intellij.execution.configurations.{RunProfile, RunnerSettings}
import com.intellij.execution.application.{ApplicationConfigurationType, ApplicationConfiguration}
import com.intellij.util.concurrency.Semaphore
import java.util.concurrent.atomic.AtomicReference
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.Disposable
import org.jetbrains.plugins.scala.util.TestUtils
import com.intellij.openapi.vfs.LocalFileSystem
import java.security.MessageDigest
import java.util
import java.nio.charset.StandardCharsets
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess

/**
 * @author Roman.Shein
 *         Date: 03.03.14
 */
abstract class ScalaDebuggerTestBase extends ScalaCompilerTestBase {
  protected def checksumsFileName = "checksums.dat"

  private var checksums: mutable.HashMap[String, Array[Byte]] = null

  protected var needMake = false

  override def setUp() {
    needMake = !testDataProjectIsValid()

    UsefulTestCase.edt(new Runnable {
      def run() {
        ScalaDebuggerTestBase.super.setUp()
        addScalaLibrary()
        addOtherLibraries()
        inWriteAction {
          ScalaFacet.createIn(myModule) { facet =>
            facet.compilerLibraryId = LibraryId("scala-compiler", LibraryLevel.Project)
          }
        }
      }
    })
  }

  /**
   * Intended for loading libraries different from scala-compiler.
   */
  protected def addOtherLibraries()

  override def setUpModule(): Unit = {
    if (needMake) super.setUpModule()
    else myModule = loadModule(getImlFile)

    PlatformTestCase.myFilesToDelete.remove(getImlFile)
  }

  protected override def tearDown(): Unit = {
    //getDebugSession.dispose()
    super.tearDown()
  }

  override def getIprFile: File = {
    val path = testDataBasePath.resolve(getName + ProjectFileType.DOT_DEFAULT_EXTENSION)
    Files.createDirectories(path.getParent)
    if (!path.toFile.exists()) Files.createFile(path).toFile else path.toFile
  }

  protected def getImlFile: File = {
    val dir = testDataBasePath.toFile
    if (dir.exists()) dir.listFiles().find {
      _.getName.endsWith(ModuleFileType.DOT_DEFAULT_EXTENSION)
    }.getOrElse(null)
    else null
  }

  override def runInDispatchThread(): Boolean = false

  override def invokeTestRunnable(runnable: Runnable): Unit = runnable.run()

  protected def getRunProfile(module: Module, className: String) = {
    val configuration: ApplicationConfiguration = new ApplicationConfiguration("app", module.getProject, ApplicationConfigurationType.getInstance)
    configuration.setModule(module)
    configuration.setMainClassName(className)
    configuration
  }

  override protected def addFileToProject(relPath: String, fileText: String) {
    val srcPath = Paths.get("src", relPath)
    if (needMake || !checkSourceFile(srcPath, fileText)) {
      needMake = true
      val file = testDataBasePath.resolve(srcPath).toFile
      if (file.exists()) file.delete()
      super.addFileToProject(relPath, fileText)
    }
  }

  protected def testDataBasePath(dataPath: String): Path = {
    val testClassName = this.getClass.getSimpleName.stripSuffix("Test")
    val path = FileSystems.getDefault.getPath(TestUtils.getTestDataPath, dataPath, testClassName, getTestName(true))
    if (path.toFile.exists()) path
    else Files.createDirectories(path)
  }

  private def testDataBasePath: Path = testDataBasePath(testDataBasePrefix)

  protected val testDataBasePrefix = "debugger"

  def getVirtualFile(file: File) = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(file)

  def md5(file: File): Array[Byte] = {
    val md = MessageDigest.getInstance("MD5")
    val isSource = file.getName.endsWith(".java") || file.getName.endsWith(".scala")
    if (isSource) {
      val text = scala.io.Source.fromFile(file, "UTF-8").mkString.replace("\r", "")
      md.digest(text.getBytes(StandardCharsets.UTF_8))
    } else {
      md.digest(Files.readAllBytes(file.toPath))
    }
  }

  private def computeChecksums(): mutable.HashMap[String, Array[Byte]] = {
    val result = new mutable.HashMap[String, Array[Byte]]
    def computeForDir(dir: File) {
      if (dir.exists) dir.listFiles().foreach { f =>
        if (f.isDirectory) computeForDir(f)
        else result += (testDataBasePath.relativize(f.toPath).toString -> md5(f))
      }
    }
    computeForDir(srcDir)
    computeForDir(outDir)
    result
  }


  protected def outDir: File = testDataBasePath.resolve("out").toFile

  protected def srcDir: File = testDataBasePath.resolve("src").toFile

  protected def saveChecksums() = {
    checksums = computeChecksums()
    val file = testDataBasePath.resolve(checksumsFileName).toFile
    if (!file.exists) Files.createFile(file.toPath)
    val oos = new ObjectOutputStream(new FileOutputStream(file))
    try {
      oos.writeObject(checksums)
    }
    finally {
      oos.close()
    }
  }

  private def loadChecksums(): Unit = {
    val file = testDataBasePath.resolve(checksumsFileName).toFile
    if (!file.exists) {
      needMake = true
      return
    }
    val ois = new ObjectInputStream(new FileInputStream(file))
    try {
      val obj = ois.readObject()
      obj match {
        case map: mutable.HashMap[String, Array[Byte]]@unchecked => checksums = map
        case _ => needMake = true
      }
    }
    finally ois.close()
  }

  private def testDataProjectIsValid(): Boolean = {
    loadChecksums()
    !needMake && checksums.keys.forall(checkFile) && getImlFile != null
  }

  private def checkSourceFile(relPath: Path, fileText: String): Boolean = {
    val file = testDataBasePath.resolve(relPath).toFile
    val oldText = scala.io.Source.fromFile(file, "UTF-8").mkString
    oldText.replace("\r", "") == fileText.replace("\r", "")
  }

  private def checkFile(relPath: String): Boolean = {
    val file = testDataBasePath.resolve(relPath).toFile
    file.exists && util.Arrays.equals(checksums(relPath), md5(file))
  }

  protected def addLibrary(libraryName: String, libraryPath: String, jarNames: String*) {
    val pathExtended = TestUtils.getTestDataPath.replace("\\", "/") + "/" + libraryPath + "/"
    VfsRootAccess.allowRootAccess(pathExtended)
    PsiTestUtil.addLibrary(myModule, libraryName, pathExtended, jarNames: _*)
  }
}
