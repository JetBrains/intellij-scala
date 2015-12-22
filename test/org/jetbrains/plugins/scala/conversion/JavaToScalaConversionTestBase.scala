package org.jetbrains.plugins.scala
package conversion


import java.io.File

import com.intellij.codeInsight.editorActions._
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter

/**
  * @author Alexander Podkhalyuzin
  */
abstract class JavaToScalaConversionTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  private val startMarker = "/*start*/"
  private val endMarker = "/*end*/"

  def folderPath: String = baseRootPath() + "conversion/"

  protected def doTest() {
    import org.junit.Assert._

    val filePath = folderPath + getTestName(false) + ".java"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    configureFromFileTextAdapter(getTestName(false) + ".java", fileText)
    val javaFile = getFileAdapter
    val offset = fileText.indexOf(startMarker)

    val startOffset = if (offset != -1) offset + startMarker.length else 0

    val lastPsi = javaFile.findElementAt(javaFile.getText.length - 1)
    var endOffset = fileText.indexOf(endMarker)
    if (endOffset == -1) endOffset = lastPsi.getPrevSibling.getTextRange.getEndOffset - 1
    else {
      val prevSibiling = javaFile.findElementAt(endOffset).getPrevSibling.getTextRange.getEndOffset
      if (prevSibiling == endOffset) endOffset = prevSibiling - 1
      else endOffset = prevSibiling
    }

    val (parts, updatedFile) = ConverterUtil.prepareDataForConversion(javaFile, Array(startOffset), Array(endOffset))

    val referenceProcessor = Extensions.getExtensions(CopyPastePostProcessor.EP_NAME)
      .find(_.isInstanceOf[JavaCopyPasteReferenceProcessor]).get

    val refs = ConverterUtil.getRefs(updatedFile, referenceProcessor, Array(startOffset), Array(endOffset), null)
    var (res, associations) = ConverterUtil.convertData(parts, refs)
    val newFile = PsiFileFactory.getInstance(getProjectAdapter).createFileFromText("dummyForJavaToScala.scala",
      ScalaFileType.SCALA_LANGUAGE, res)

    ConverterUtil.addImportsForAssociations(associations, newFile, 0, getProjectAdapter)
    ConverterUtil.runInspections(newFile, getProjectAdapter, 0, newFile.getText.length)
    res = CodeStyleManager.getInstance(getProjectAdapter).reformat(newFile).getText

    val text = lastPsi.getText
    val output = lastPsi.getNode.getElementType match {
      case JavaTokenType.END_OF_LINE_COMMENT => text.substring(2).trim
      case JavaTokenType.C_STYLE_COMMENT =>
        text.substring(2, text.length - 2).trim
      case _ => assertTrue("Test result must be in last comment statement.", false)
    }
    assertEquals(output, res.trim)
  }
}