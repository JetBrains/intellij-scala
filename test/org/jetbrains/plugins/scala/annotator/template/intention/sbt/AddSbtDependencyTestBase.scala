package org.jetbrains.plugins.scala.annotator.template.intention.sbt

import java.io.File

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.PlatformTestCase
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.language.{SbtFileImpl, SbtFileType}
import org.jetbrains.sbt.resolvers.ArtifactInfo

/**
  * Created by afonichkin on 8/29/17.
  */
class AddSbtDependencyTestBase extends PlatformTestCase {
  def toArtifactInfo(s: String) = {
    val parts = s.replaceAll("\"", "").split("%").map(_.trim)
    ArtifactInfo(parts(0), parts(1), parts(2))
  }

  def testdataPath: String = TestUtils.getTestDataPath + "/annotator/intention/sbt/"

  def loadTestFile(fileName: String): SbtFileImpl = {
    val filePath = testdataPath + fileName
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    PsiFileFactory.getInstance(getProject).createFileFromText(fileName , SbtFileType, fileText).asInstanceOf[SbtFileImpl]
  }
}
