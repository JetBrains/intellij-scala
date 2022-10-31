package org.jetbrains.plugins.scala.lang.adjustTypes

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.TestUtils.ExpectedResultFromLastComment
import org.jetbrains.plugins.scala.util.{TestUtils, WriteCommandActionEx}

import java.io.File

abstract class AdjustTypesTestBase extends ScalaLightCodeInsightFixtureTestCase {
  private val startMarker = "/*start*/"
  private val endMarker = "/*end*/"

  protected def folderPath = getTestDataPath + "adjustTypes/"

  protected override def sourceRootPath: String = folderPath

  protected def doTest(): Unit = {
    import _root_.org.junit.Assert._
    val filePath = folderPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.refreshAndFindFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")

    var fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))

    val startOffset = fileText.indexOf(startMarker)
    assert(startOffset != -1, "Not specified start marker in test case. Use /*start*/ in scala file for this.")
    fileText = fileText.replace(startMarker, "")

    val endOffset = fileText.indexOf(endMarker)
    assert(endOffset != -1, "Not specified end marker in test case. Use /*end*/ in scala file for this.")
    fileText = fileText.replace(endMarker, "")

    configureFromFileText(getTestName(false) + ".scala", fileText)
    val scalaFile = getFile.asInstanceOf[ScalaFile]
    val element = PsiTreeUtil.findElementOfClassAtRange(scalaFile, startOffset, endOffset, classOf[PsiElement])

    WriteCommandActionEx.runWriteCommandAction(getProject, () => {
      ScalaPsiUtil.adjustTypes(element)
      UsefulTestCase.doPostponedFormatting(getProject)
    })

    val ExpectedResultFromLastComment(res, output) = TestUtils.extractExpectedResultFromLastComment(scalaFile)
    assertEquals(output, res)
  }
}