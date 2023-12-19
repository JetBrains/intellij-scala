package org.jetbrains.plugins.scala.conversion

import com.intellij.openapi.command.{CommandProcessor, UndoConfirmationPolicy}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem, VirtualFile}
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.{executeWriteActionCommand, inWriteAction}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings
import org.junit.Assert._

import java.io.File

//noinspection InstanceOf
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
    val filePath = (folderPath + testFileName).replace(File.separatorChar, '/')

    try {
      doTestForFilePath(testFileName, filePath)
    } catch {
      case ex: Throwable =>
        System.err.println(s"Test file path: $filePath")
        throw ex
    } finally {
      set(getProject, oldSettings.asInstanceOf[ScalaCodeStyleSettings])
    }
  }

  private def readFileContent(file: VirtualFile) : String = {
    val content = FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8)
    StringUtil.convertLineSeparators(content)
  }

  protected def doTestForFilePath(
    testFileName: String,
    filePath: String
  ): Unit = {
    val testFile = LocalFileSystem.getInstance.findFileByPath(filePath)
    assert(testFile != null, s"file $filePath not found")

    val testFileText = readFileContent(testFile)
    val javaFile: PsiJavaFile = configureFromFileText(testFileName, testFileText).asInstanceOf[PsiJavaFile]

    val expectedResult: String = javaFile.findElementAt(javaFile.getText.length - 1) match {
      case lastPsiComment: PsiComment =>
        val commentContent = getCommentContent(lastPsiComment)
        inWriteAction {
          executeWriteActionCommand(
            () => {
              lastPsiComment.delete() // delete from original java file, the comment will remain in detached mode
            },
            "deleting test comment",
            UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION
          )(getProject)
        }
        commentContent
      case e =>
        val expectedScalaFileName = filePath.replace(".java", ".scala")
        val expectedScalaFile = LocalFileSystem.getInstance.findFileByPath(expectedScalaFileName)
        if (expectedScalaFile != null)
          readFileContent(expectedScalaFile)
        else {
          fail(
            s"""No expected Scala content found.
               |It should be located in the last element in ${testFile.getName}
               |Or placed in a separate file ${new File(expectedScalaFileName).getName}""".stripMargin
          ).asInstanceOf[Nothing]
        }
    }

    doTest(
      javaFile,
      expectedResult
    )
  }

  protected def doTest(
    @Language("JAVA") javaFileText: String,
    @Language("Scala") expectedScalaFileText: String
  ): Unit = {
    val javaFile: PsiJavaFile = configureFromFileText("dummy.java", javaFileText).asInstanceOf[PsiJavaFile]
    doTest(javaFile, expectedScalaFileText)
  }

  private def doTest(
    javaFile: PsiJavaFile,
    expectedScalaFileText: String
  ): Unit = {
    val javaFileText = javaFile.getText

    val startMarkerOffset = javaFileText.indexOf(startMarker)
    val endMarkerOffset = javaFileText.indexOf(endMarker)

    val factory = PsiFileFactory.getInstance(getProject)
    val scalaFile = factory.createFileFromText("dummyForJavaToScala.scala", ScalaLanguage.INSTANCE, "")
    CommandProcessor.getInstance.executeCommand(getProject, () => {
      inWriteAction {
        val convertFullFile = startMarkerOffset == -1 || endMarkerOffset == -1
        if (convertFullFile) {
          //truly invoke full-file "Convert Java to Scala" action
          ConvertJavaToScalaAction.convertToScalaFile(javaFile, scalaFile)
        } else {
          val startOffset = startMarkerOffset + startMarker.length
          val endOffset = endMarkerOffset

          val elements = ConverterUtil.collectTopElements(startOffset, endOffset, javaFile)
          val convertedText = JavaToScala.convertPsisToText(elements)
          updateDocumentTextAndCommit(scalaFile, convertedText)

          ConverterUtil.cleanCode(scalaFile, getProject, 0, scalaFile.getText.length)
          CodeStyleManager.getInstance(getProject).reformat(scalaFile)
        }
      }
    }, null, null)

    val scalaFileText = scalaFile.getText
    val actualResult = scalaFileText.trim.stripPrefix(startMarker).stripSuffix(endMarker).trim
    assertEquals(expectedScalaFileText, actualResult)
  }

  private def updateDocumentTextAndCommit(scalaFile: PsiFile, convertedScalaText: String): Unit = {
    val project = scalaFile.getProject
    val document = PsiDocumentManager.getInstance(project).getDocument(scalaFile)
    document.insertString(0, convertedScalaText)
    document.commit(project)
  }

  private def getCommentContent(comment: PsiComment): String = {
    val lastCommentText = comment.getText
    comment.getNode.getElementType match {
      case JavaTokenType.END_OF_LINE_COMMENT =>
        lastCommentText.substring(2).trim
      case JavaTokenType.C_STYLE_COMMENT =>
        lastCommentText.substring(2, lastCommentText.length - 2).trim
      case _ =>
        fail("Test result must be in last comment statement").asInstanceOf[Nothing]
    }
  }
}