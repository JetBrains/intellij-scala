package org.jetbrains.plugins.scala
package lang
package checkers
package checkPrivateAccess

import java.io.File

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.psi.PsiMember
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils
import org.junit.Assert._

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.10.2009
 */

abstract class CheckPrivateAccessTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  val refMarker = "/*ref*/"

  protected def folderPath = baseRootPath + "checkers/checkPrivateAccess/"

  protected def doTest() {
    val filePath = folderPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assertNotNull("file " + filePath + " not found", file)

    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    configureFromFileTextAdapter(getTestName(false) + ".scala", fileText)
    val scalaFile = getFileAdapter.asInstanceOf[ScalaFile]
    val offset = fileText.indexOf(refMarker) + refMarker.length
    assertNotEquals("Not specified caret marker in test case. Use " + refMarker + " in scala file for this.", offset, refMarker.length - 1)

    val elem = scalaFile.findElementAt(offset).getParent
    if (!elem.isInstanceOf[ScReference])
      fail("Ref marker should point on reference")
    val ref = elem.asInstanceOf[ScReference]
    val resolve: PsiMember = PsiTreeUtil.getParentOfType(ref.resolve(), classOf[PsiMember], false)

    val actual = ResolveUtils.isAccessible(resolve, elem)
    
    val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)
    val text = lastPsi.getText
    val expected = lastPsi.getNode.getElementType match {
      case ScalaTokenTypes.tLINE_COMMENT => text.substring(2).trim
      case ScalaTokenTypes.tBLOCK_COMMENT | ScalaTokenTypes.tDOC_COMMENT =>
        text.substring(2, text.length - 2).trim
      case _ => fail("Test result must be in last comment statement.")
    }

    if (shouldPass) assertEquals("Result differs from reference: ", expected, actual.toString)
    else fail("Test has passed, but was supposed to fail")
  }

  protected def shouldPass: Boolean = true
}
