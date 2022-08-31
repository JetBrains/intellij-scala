package org.jetbrains.plugins.scala.lang.types.utils

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.junit.Assert._

import java.io.File
import scala.reflect._

abstract class ScPsiElementAssertionTestBase[T <: PsiElement : ClassTag]
  extends ScalaLightCodeInsightFixtureTestCase {

  private val startExprMarker = "/*start*/"
  private val endExprMarker = "/*end*/"

  private lazy val psiClazz: Class[T] = classTag[T].runtimeClass.asInstanceOf[Class[T]]

  def folderPath: String

  def computeRepresentation(t: T): Either[String, String]

  protected def doTest(): Unit = {
    val filePath = folderPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    configureFromFileText(getTestName(false) + ".scala", fileText)
    val scalaFile = getFile.asInstanceOf[ScalaFile]
    val offset = fileText.indexOf(startExprMarker)
    val startOffset = offset + startExprMarker.length

    assert(offset != -1, "Not specified start marker in test case. Use /*start*/ in scala file for this.")
    val endOffset = fileText.indexOf(endExprMarker)
    assert(endOffset != -1, "Not specified end marker in test case. Use /*end*/ in scala file for this.")

    val addOne = if (PsiTreeUtil.getParentOfType(scalaFile.findElementAt(startOffset), psiClazz) != null) 0 else 1 //for xml tests
    val t: T = PsiTreeUtil.findElementOfClassAtRange(scalaFile, startOffset + addOne, endOffset, psiClazz)
    assert(t != null, "Not specified element in range.")
    computeRepresentation(t) match {
      case Right(res) =>
        val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)
        val text = lastPsi.getText
        val output = lastPsi.getNode.getElementType match {
          case ScalaTokenTypes.tLINE_COMMENT => text.substring(2).trim
          case ScalaTokenTypes.tBLOCK_COMMENT | ScalaTokenTypes.tDOC_COMMENT =>
            text.substring(2, text.length - 2).trim
          case _ => assertTrue("Test result must be in last comment statement.", false)
        }
        assertEquals(output, res)
      case Left(err) => fail(err)
    }
  }
}
