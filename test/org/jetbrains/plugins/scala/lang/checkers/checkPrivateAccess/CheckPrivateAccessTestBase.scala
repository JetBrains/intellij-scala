package org.jetbrains.plugins.scala
package lang
package checkers
package checkPrivateAccess

import _root_.scala.collection.mutable.ArrayBuffer
import _root_.scala.util.Sorting
import base.{ScalaLightPlatformCodeInsightTestCaseAdapter, ScalaPsiTestCase}
import com.intellij.codeInsight.hint.{HintUtil, ShowParameterInfoContext}
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.openapi.fileEditor.{OpenFileDescriptor, FileEditorManager}
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem, VirtualFile}
import com.intellij.psi.util.PsiTreeUtil
import java.awt.Color
import java.io.File
import java.lang.String
import com.intellij.vcsUtil.VcsUtil
import lexer.ScalaTokenTypes
import psi.api.ScalaFile
import parameterInfo.ScalaFunctionParameterInfoHandler
import psi.api.base.ScReferenceElement
import resolve.ResolveUtils
import com.intellij.psi.{PsiMember, PsiComment, PsiManager, PsiElement}
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.io.FileUtil

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.10.2009
 */

abstract class CheckPrivateAccessTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  val refMarker = "/*ref*/"

  protected def folderPath = baseRootPath() + "checkers/checkPrivateAccess/"

  protected def doTest() {
    import _root_.junit.framework.Assert._
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
      case _ => assertTrue("Test result must be in last comment statement.", false)
    }
    assertEquals(output, res.toString)
  }
}
