package org.jetbrains.sbt
package lang.completion

import java.io.File

import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs._
import com.intellij.testFramework.{LightVirtualFile, UsefulTestCase}
import org.jetbrains.plugins.scala.lang.completion
import org.jetbrains.sbt.resolvers.indexes.ResolverIndex
import scala.collection.JavaConverters._

/**
 * @author Nikolay Obedin
 * @since 7/17/14.
 */

abstract class CompletionTestBase extends completion.CompletionTestBase with MockSbt {

  override val sbtVersion = Sbt.LatestVersion

  override def folderPath: String = super.folderPath + "Sbt/"
  override def testFileExt = ".sbt"


  /**
   * @inheritdoc
   * Instead of using original file copy its contents into
   * mock file prepending implicit SBT imports
   */
  override def loadFile: (String, LightVirtualFile) = {
    val fileName = getTestName(false) + testFileExt
    val filePath = folderPath + fileName
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val fileText =
      Sbt.DefaultImplicitImports.map("import " + _).mkString("\n") + "\n" +
      StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    val mockFile = new LightVirtualFile(fileName, fileText)
    assert(mockFile != null, "Mock file can not be created")
    (fileName, mockFile)
  }

  override def loadAndSetFileText(filePath: String, file: VirtualFile): String = {
    val fileText = new String(file.contentsToByteArray())
    configureFromFileTextAdapter (filePath, fileText)
    fileText
  }

  override def checkResult(got: Array[String], _expected: String) {
    val expected = _expected.split("\n")
    UsefulTestCase.assertContainsElements[String](got.toSeq.asJava, expected.toSeq.asJava)
  }

  override def setUp() {
    super.setUpWithoutScalaLib()
    setUpLibraries()
    inWriteAction {
      StartupManager.getInstance(project) match {
        case manager: StartupManagerImpl => manager.startCacheUpdate()
      }
    }
    FileUtil.delete(ResolverIndex.DEFAULT_INDEXES_DIR)
  }

  override def tearDown(): Unit = {
    super.tearDown()
    FileUtil.delete(ResolverIndex.DEFAULT_INDEXES_DIR)
  }
}

