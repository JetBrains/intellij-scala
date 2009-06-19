package org.jetbrains.plugins.scala.refactoring.inline


import base.ScalaPsiTestCase
import com.intellij.psi.PsiManager
import lang.psi.api.ScalaFile
import java.io.File
import com.intellij.openapi.vfs.LocalFileSystem

/**
 * User: Alexander Podkhalyuzin
 * Date: 16.06.2009
 */

class InlineRefactoringTestBase extends ScalaPsiTestCase {
  id : ScalaPsiTestCase =>
  val caretMarker = "/*caret*/"

  override protected def rootPath = super.rootPath + "inline/"

  protected def doTest: Unit = {
    import _root_.junit.framework.Assert._
    val filePath = rootPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val scalaFile: ScalaFile = PsiManager.getInstance(myProject).findFile(file).asInstanceOf[ScalaFile]
    val fileText = scalaFile.getText
    val offset = fileText.indexOf(caretMarker)
    assert(offset != -1, "Not specified caret marker in test case. Use /*caret*/ in scala file for this.")
    
  }
}