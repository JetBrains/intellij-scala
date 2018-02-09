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
import junit.framework.Assert
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.10.2009
 */

abstract class CheckPrivateAccessTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  val refMarker = "/*ref*/"

  protected def folderPath = baseRootPath() + "checkers/checkPrivateAccess/"

  protected def doTest() {
    val filePath = folderPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    configureFromFileTextAdapter(getTestName(false) + ".scala", fileText)
    val scalaFile = getFileAdapter.asInstanceOf[ScalaFile]
    val offset = fileText.indexOf(refMarker) + refMarker.length
    assert(offset != refMarker.length - 1, "Not specified caret marker in test case. Use " + refMarker + " in scala file for this.")
    val elem = scalaFile.findElementAt(offset).getParent
    if (!elem.isInstanceOf[ScReferenceElement]) assert(assertion = true, message = "Ref marker should point on reference")
    val ref = elem.asInstanceOf[ScReferenceElement]
    val resolve: PsiMember = PsiTreeUtil.getParentOfType(ref.resolve(), classOf[PsiMember], false)

    val res = "" + ResolveUtils.isAccessible(resolve, elem)
    
    val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)
    val text = lastPsi.getText
    val output = lastPsi.getNode.getElementType match {
      case ScalaTokenTypes.tLINE_COMMENT => text.substring(2).trim
      case ScalaTokenTypes.tBLOCK_COMMENT | ScalaTokenTypes.tDOC_COMMENT =>
        text.substring(2, text.length - 2).trim
      case _ => Assert.assertTrue("Test result must be in last comment statement.", false)
    }
    Assert.assertTrue(Assert.format(failureMessage, output, res.toString), output == res.toString && shouldPass)
  }

  protected def shouldPass: Boolean = true
  protected def failureMessage: String = if (shouldPass) "Result differs from reference: " else "Test has passed, but was supposed to fail: "
}
