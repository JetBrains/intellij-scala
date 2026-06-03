package org.jetbrains.plugins.scala.lang.types.utils

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{PathExt, StringExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ExpectedResultFromLastComment
import org.junit.Assert._

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import scala.reflect._

abstract class ScPsiElementAssertionTestBase[T <: PsiElement : ClassTag]
  extends ScalaLightCodeInsightFixtureTestCase {

  private val startExprMarker = "/*start*/"
  private val endExprMarker = "/*end*/"

  private lazy val psiClazz: Class[T] = classTag[T].runtimeClass.asInstanceOf[Class[T]]

  def folderPath: Path = Path.of(getTestDataPath)

  def computeRepresentation(t: T): Either[String, String]

  protected def doTest(): Unit = {
    val fileName = getTestName(false) + ".scala"
    val filePath = folderPath / fileName
    val fileText = filePath.readAllBytesToString(StandardCharsets.UTF_8).withNormalizedSeparator
    configureFromFileText(fileName, fileText)
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
        val ExpectedResultFromLastComment(_, output) = TestUtils.extractExpectedResultFromLastComment(scalaFile)
        assertEquals(output, res)
      case Left(err) => fail(err)
    }
  }
}
