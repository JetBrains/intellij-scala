package org.jetbrains.plugins.scala.conversion

import com.intellij.openapi.command.CommandProcessor

import java.io.File
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings

import scala.collection.mutable

abstract class JavaToScalaConversionTestBase extends ScalaLightCodeInsightFixtureTestCase {

  import TypeAnnotationSettings._

  private val startMarker = "/*start*/"
  private val endMarker = "/*end*/"

  def folderPath: String = s"$getTestDataPath../../conversion/testdata/conversion/"

  protected def getDefaultSettings: ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(getProject)

  protected def getRemoveTypeAnnotationsForAllMembersSettings: ScalaCodeStyleSettings = {
    val settings = getDefaultSettings
    settings.TYPE_ANNOTATION_PUBLIC_MEMBER = false
    settings.TYPE_ANNOTATION_PROTECTED_MEMBER = false
    settings.TYPE_ANNOTATION_PRIVATE_MEMBER = false
    settings.TYPE_ANNOTATION_LOCAL_DEFINITION = false
    settings
  }

  protected def doTest(
    typeAnnotationSettings: ScalaCodeStyleSettings = alwaysAddType(getDefaultSettings)
  ): Unit = {
    val testFileName = getTestName(false) + ".java"
    doTest(typeAnnotationSettings, testFileName)
  }

  protected def doTest(
    typeAnnotationSettings: ScalaCodeStyleSettings,
    testFileName: String
  ): Unit = {
    val oldSettings: Any = getDefaultSettings.clone
    set(getProject, typeAnnotationSettings)
    val filePath = folderPath + testFileName

    try {
      doTestForFilePath(typeAnnotationSettings, testFileName, filePath)
    } catch {
      case ex: Throwable =>
        System.err.println(s"Test file path: $filePath")
        throw ex
    } finally {
      set(getProject, oldSettings.asInstanceOf[ScalaCodeStyleSettings])
    }
  }

  protected def doTestForFilePath(
    typeAnnotationSettings: ScalaCodeStyleSettings,
    testFileName: String,
    filePath: String
  ): Unit = {
    import org.junit.Assert._
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    configureFromFileText(testFileName, fileText)
    val javaFile = getFile
    val offset = fileText.indexOf(startMarker)

    val startOffset = if (offset != -1) offset + startMarker.length else 0

    val lastPsi = javaFile.findElementAt(javaFile.getText.length - 1)
    var endOffset = fileText.indexOf(endMarker)
    if (endOffset == -1) endOffset = lastPsi.getTextRange.getStartOffset

    val buf = ConverterUtil.collectTopElements(startOffset, endOffset, javaFile)
    var res = JavaToScala.convertPsisToText(buf, getUsedComments(offset, endOffset, lastPsi, javaFile))
    val newFile = PsiFileFactory.getInstance(getProject)
      .createFileFromText("dummyForJavaToScala.scala", ScalaLanguage.INSTANCE, res)

    CommandProcessor.getInstance.executeCommand(getProject, () => {
      inWriteAction {
        ConverterUtil.cleanCode(newFile, getProject, 0, newFile.getText.length)
        res = CodeStyleManager.getInstance(getProject).reformat(newFile).getText
      }
    }, null, null)

    val text = lastPsi.getText
    val output = lastPsi.getNode.getElementType match {
      case JavaTokenType.END_OF_LINE_COMMENT => text.substring(2).trim
      case JavaTokenType.C_STYLE_COMMENT =>
        text.substring(2, text.length - 2).trim
      case _ => assertTrue("Test result must be in last comment statement.", false)
    }

    assertEquals(output, res.trim)
  }

  private def getUsedComments(startOffset: Int, endOffset: Int,
                              lastComment: PsiElement, javaFile: PsiFile) = {
    val usedComments = mutable.HashSet.empty[PsiElement]
    val startComment = javaFile.findElementAt(startOffset)
    val endComment = javaFile.findElementAt(endOffset)
    if (startComment != null) usedComments += startComment
    if (endComment != null) usedComments += endComment
    if (lastComment != null) usedComments += lastComment
    usedComments
  }
}