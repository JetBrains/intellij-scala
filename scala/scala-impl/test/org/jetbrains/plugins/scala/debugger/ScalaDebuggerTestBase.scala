package org.jetbrains.plugins.scala
package debugger

import java.io._
import java.nio.file.Path
import java.security.MessageDigest
import java.util

import com.intellij.execution.application.{ApplicationConfiguration, ApplicationConfigurationType}
import com.intellij.ide.highlighter.{ModuleFileType, ProjectFileType}
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.{LocalFileSystem, VfsUtil, VirtualFile}
import com.intellij.testFramework._
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert

import scala.collection.mutable
import scala.util.Try

/**
  * @author Roman.Shein
  *         Date: 03.03.14
  */
abstract class ScalaDebuggerTestBase extends ScalaCompilerTestBase {
  protected def checksumsFileName = "checksums.dat"

  protected val testDataBasePrefix = "debugger"

  private var checksums: Checksums = _

  protected var needMake = false

  private val sourceFiles = mutable.HashMap[String, String]()

  override def setUp() {
    val testDataValid = testDataProjectIsValid()
    if (!testDataValid) {
      needMake = true
      val testDataProjectPath = testDataBasePath
      if (testDataProjectPath.exists()) FileUtil.delete(testDataProjectPath)
    }

    EdtTestUtil.runInEdtAndWait(() => {
      ScalaDebuggerTestBase.super.setUp()
      checkOrAddAllSourceFiles()
    })
  }

  override def setUpModule(): Unit = {
    if (needMake) super.setUpModule()
    else myModule = loadModule(getImlFile.getAbsolutePath)

    myFilesToDelete.remove(getImlFile)
  }

  override def getProjectDirOrFile: Path = {
    val file = new File(testDataBasePath, testClassName + ProjectFileType.DOT_DEFAULT_EXTENSION)
    FileUtil.createIfDoesntExist(file)
    file.toPath
  }

  protected def getImlFile: File = {
    if (testDataBasePath.exists()) testDataBasePath.listFiles().find {
      _.getName.endsWith(ModuleFileType.DOT_DEFAULT_EXTENSION)
    }.orNull
    else null
  }

  override def runInDispatchThread(): Boolean = false

  protected def getRunProfile(module: Module, className: String) = {
    val configuration: ApplicationConfiguration = new ApplicationConfiguration("app", module.getProject, ApplicationConfigurationType.getInstance)
    configuration.setModule(module)
    configuration.setMainClassName(className)
    configuration
  }

  override protected def addFileToProjectSources(fileName: String, fileText: String): VirtualFile = {
    def virtualFileExists(file: File): Boolean =
      Try(getVirtualFile(file).exists()).getOrElse(false)

    val file = getFileInSrc(fileName)
    if (needMake || !fileWithTextExists(file, fileText)) {
      needMake = true
      if (file.exists() || virtualFileExists(file)) {
        val vFile = getVirtualFile(file)
        inWriteAction(VfsUtil.saveText(vFile, fileText))
        vFile
      } else {
        super.addFileToProjectSources(fileName, fileText)
      }
    } else {
      getVirtualFile(file)
    }
  }

  protected def addSourceFile(relPathInSrc: String, fileText: String) = {
    sourceFiles += relPathInSrc -> fileText
  }

  def checkOrAddAllSourceFiles(): Unit = {
    if (sourceFiles.exists {
      case (path, text) => !fileWithTextExists(new File(path), text)
    }) {
      sourceFiles.foreach {
        case (path, text) => addFileToProjectSources(path, text)
      }
    }
  }

  protected def addFileToProject(fileText: String) {
    Assert.assertTrue(s"File should start with `object $mainClassName`", fileText.startsWith(s"object $mainClassName"))
    addFileToProjectSources(mainFileName, fileText)
  }

  protected def getFileInSrc(fileName: String): File = {
    if (!srcDir.exists()) srcDir.mkdir()
    new File(srcDir, fileName)
  }

  protected def testClassName: String = this.getClass.getSimpleName.stripSuffix("Test")

  protected def testDataBasePath(dataPath: String): File = {
    val testDataDir = new File(TestUtils.getTestDataPath, dataPath)
    val classTestsDir = new File(testDataDir, testClassName)
    if (classTestsDir.exists()) classTestsDir
    else {
      FileUtil.createDirectory(classTestsDir)
      classTestsDir
    }
  }

  protected def mainClassName = getTestName(false)

  protected def mainFileName = s"$mainClassName.scala"

  private def testDataBasePath: File = testDataBasePath(testDataBasePrefix)

  def getVirtualFile(file: File) = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(file)

  def md5(file: File): Array[Byte] = {
    import extensions._
    val md = MessageDigest.getInstance("MD5")
    val isSource = file.getName.endsWith(".java") || file.getName.endsWith(".scala")
    if (isSource) {
      val text = FileUtil.loadFile(file, "UTF-8").replace("\r", "")
      md.digest(text.getBytes("UTF8"))
    } else {
      using(new FileInputStream(file)) { s =>
        md.digest(FileUtil.loadBytes(s))
      }
    }
  }

  private def computeChecksums(): Checksums = {
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
    new Checksums(result, version.minor)
  }


  protected def outDir: File = new File(testDataBasePath, "out")

  protected def srcDir: File = new File(testDataBasePath, "src")

  protected def saveChecksums(): Unit = {
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

  private def loadChecksums(): Boolean = {
    val file = new File(testDataBasePath, checksumsFileName)
    if (!file.exists) {
      return false
    }
    val ois = new ObjectInputStream(new FileInputStream(file))
    val result =
      try {
        val obj = ois.readObject()
        obj match {
          case cs: Checksums => checksums = cs; true
          case _ => false
        }
      }
      catch {
        case _: IOException => false
      }
      finally ois.close()
    result
  }

  private def testDataProjectIsValid(): Boolean = {
    sameSourceFiles() && loadChecksums() &&
      checksums.scalaVersion == version.minor &&
      checksums.fileToMd5.keys.forall(checkFile) &&
      getImlFile != null
  }

  private def sameSourceFiles(): Boolean = {
    def numberOfFiles(dir: File): Int = dir match {
      case d: File if d.isDirectory =>
        val listFiles = d.listFiles()
        Assert.assertTrue(s"listFiles() is null for directory: ${d.getAbsolutePath}", listFiles != null)
        listFiles.map(numberOfFiles).sum
      case f => 1
    }
    val existingFilesNumber = numberOfFiles(srcDir)
    sourceFiles.size == existingFilesNumber && sourceFiles.forall {
      case (relPath, text) => fileWithTextExists(new File(srcDir, relPath), text)
    }
  }

  private def fileWithTextExists(file: File, fileText: String): Boolean = {
    if (!file.exists()) false
    else {
      val oldText = FileUtil.loadFile(file, "UTF-8")
      oldText.replace("\r", "") == fileText.replace("\r", "")
    }
  }

  private def checkFile(relPath: String): Boolean = {
    val file = new File(testDataBasePath, relPath)
    file.exists && util.Arrays.equals(checksums.fileToMd5(relPath), md5(file))
  }
}

private class Checksums(val fileToMd5: mutable.HashMap[String, Array[Byte]], val scalaVersion: String)
  extends scala.Serializable