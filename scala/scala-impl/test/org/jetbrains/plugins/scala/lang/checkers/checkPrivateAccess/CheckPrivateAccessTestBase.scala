package org.jetbrains.plugins.scala.lang.checkers.checkPrivateAccess

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiMember
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PathExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ExpectedResultFromLastComment
import org.junit.Assert._

import java.nio.charset.StandardCharsets
import java.nio.file.Path

abstract class CheckPrivateAccessTestBase extends ScalaLightCodeInsightFixtureTestCase {
  val refMarker = "/*ref*/"

  protected def folderPath: Path = Path.of(getTestDataPath, "checkers", "checkPrivateAccess")

  override protected def shouldPass: Boolean = true

  protected def doTest(): Unit = {
    val fileName = getTestName(false) + ".scala"
    val filePath = folderPath / fileName
    val fileText = StringUtil.convertLineSeparators(filePath.readAllBytesToString(StandardCharsets.UTF_8))
    configureFromFileText(fileName, fileText)

    val scalaFile = getFile.asInstanceOf[ScalaFile]
    val offset = fileText.indexOf(refMarker) + refMarker.length
    assertNotEquals("Not specified caret marker in test case. Use " + refMarker + " in scala file for this.", offset, refMarker.length - 1)

    //noinspection DfaNpeOnInvocation
    val elem = scalaFile.findElementAt(offset).getParent
    if (!elem.is[ScReference])
      fail("Ref marker should point on reference")
    val ref = elem.asInstanceOf[ScReference]
    val resolve: PsiMember = PsiTreeUtil.getParentOfType(ref.resolve(), classOf[PsiMember], false)

    val actual = ResolveUtils.isAccessible(resolve, elem)

    val ExpectedResultFromLastComment(_, expected) = TestUtils.extractExpectedResultFromLastComment(scalaFile)

    if (shouldPass) {
      assertEquals("Wrong reference accessibility: ", expected, actual.toString)
    }
    else {
      if (expected == actual.toString) {
        fail("Test has passed, but was supposed to fail")
      }
    }
  }
}
