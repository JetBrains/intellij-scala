package org.jetbrains.plugins.scala
package debugger

import java.io._
import java.security.MessageDigest
import java.util

import com.intellij.execution.application.{ApplicationConfiguration, ApplicationConfigurationType}
import com.intellij.ide.highlighter.{ModuleFileType, ProjectFileType}
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.{PlatformTestCase, PsiTestUtil, UsefulTestCase}
import org.jetbrains.plugins.scala.util.TestUtils

import scala.collection.mutable

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
        addScalaSdk()
        addOtherLibraries()
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
    val file = new File(testDataBasePath, getName + ProjectFileType.DOT_DEFAULT_EXTENSION)
    FileUtil.createIfDoesntExist(file)
    file
  }

  protected def getImlFile: File = {
    if (testDataBasePath.exists()) testDataBasePath.listFiles().find {
      _.getName.endsWith(ModuleFileType.DOT_DEFAULT_EXTENSION)
    }.orNull
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

  override protected def addFileToProject(fileName: String, fileText: String) {
    if (!srcDir.exists()) srcDir.mkdir()
    val file = new File(srcDir, fileName)
    if (needMake || !checkSourceFile(file, fileText)) {
      needMake = true
      if (file.exists()) file.delete()
      super.addFileToProject(fileName, fileText)
    }
  }

  protected def testDataBasePath(dataPath: String): File = {
    val testClassName = this.getClass.getSimpleName.stripSuffix("Test")
    val testDataDir = new File(TestUtils.getTestDataPath, dataPath)
    val classTestsDir = new File(testDataDir, testClassName)
    val file = new File(classTestsDir, getTestName(true))
    if (file.exists()) file
    else {
      FileUtil.createDirectory(file)
      file
    }
  }

  private def testDataBasePath: File = testDataBasePath(testDataBasePrefix)

  protected val testDataBasePrefix = "debugger"

  def getVirtualFile(file: File) = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(file)

  def md5(file: File): Array[Byte] = {
    val md = MessageDigest.getInstance("MD5")
    val isSource = file.getName.endsWith(".java") || file.getName.endsWith(".scala")
    if (isSource) {
      val text = scala.io.Source.fromFile(file, "UTF-8").mkString.replace("\r", "")
      md.digest(text.getBytes("UTF8"))
    } else {
      md.digest(FileUtil.loadBytes(new FileInputStream(file)))
    }
  }

  private def computeChecksums(): mutable.HashMap[String, Array[Byte]] = {
    val result = new mutable.HashMap[String, Array[Byte]]
    def computeForDir(dir: File) {
      if (dir.exists) dir.listFiles().foreach { f =>
        if (f.isDirectory) computeForDir(f)
        else {
          result += (testDataBasePath.toURI.relativize(f.toURI).toString -> md5(f))
        }
      }
    }
    computeForDir(srcDir)
    computeForDir(outDir)
    result
  }


  protected def outDir: File = new File(testDataBasePath, "out")

  protected def srcDir: File = new File(testDataBasePath, "src")

  protected def saveChecksums() = {
    checksums = computeChecksums()
    val file = new File(testDataBasePath, checksumsFileName)
    FileUtil.createIfDoesntExist(file)
    val oos = new ObjectOutputStream(new FileOutputStream(file))
    try {
      oos.writeObject(checksums)
    }
    finally {
      oos.close()
    }
  }

  private def loadChecksums(): Unit = {
    val file = new File(testDataBasePath, checksumsFileName)
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

  private def checkSourceFile(file: File, fileText: String): Boolean = {
    val oldText = scala.io.Source.fromFile(file, "UTF-8").mkString
    oldText.replace("\r", "") == fileText.replace("\r", "")
  }

  private def checkFile(relPath: String): Boolean = {
    val file = new File(testDataBasePath, relPath)
    file.exists && util.Arrays.equals(checksums(relPath), md5(file))
  }

  protected def addLibrary(libraryName: String, libraryPath: String, jarNames: String*) {
    val pathExtended = TestUtils.getTestDataPath.replace("\\", "/") + "/" + libraryPath + "/"
    VfsRootAccess.allowRootAccess(pathExtended)
    PsiTestUtil.addLibrary(myModule, libraryName, pathExtended, jarNames: _*)
  }
}
