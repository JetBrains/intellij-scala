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

/**
 * @author Nikolay Obedin
 * @since 7/17/14.
 */

abstract class CompletionTestBase extends completion.CompletionTestBase with MockSbt {

  override val sbtVersion = Sbt.LatestVersion

  override def folderPath  = super.folderPath + "Sbt/"
  override def testFileExt = ".sbt"


  /**
   * @inheritdoc
   * Instead of using original file copy its contents into
   * mock file prepending implicit SBT imports
   */
  override def loadFile = {
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

  override def loadAndSetFileText(filePath: String, file: VirtualFile) = {
    val fileText = new String(file.contentsToByteArray())
    configureFromFileTextAdapter (filePath, fileText)
    fileText
  }

  override def checkResult(got: Array[String], _expected: String) {
    import scala.collection.JavaConversions._
    val expected = _expected.split("\n")
    UsefulTestCase.assertContainsElements[String](got.toSet.toSeq, expected.toSeq)
  }

  override def setUp() {
    super.setUpWithoutScalaLib()
    addSbtAsModuleDependency(getModuleAdapter)
    inWriteAction(StartupManager.getInstance(getProjectAdapter).asInstanceOf[StartupManagerImpl].startCacheUpdate())
    FileUtil.delete(ResolverIndex.DEFAULT_INDEXES_DIR)
  }

  override def tearDown(): Unit = {
    super.tearDown()
    FileUtil.delete(ResolverIndex.DEFAULT_INDEXES_DIR)
  }
}

