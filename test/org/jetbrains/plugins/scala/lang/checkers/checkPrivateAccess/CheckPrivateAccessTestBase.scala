package org.jetbrains.plugins.scala
package lang
package checkers
package checkPrivateAccess

import _root_.scala.collection.mutable.ArrayBuffer
import _root_.scala.util.Sorting
import base.ScalaPsiTestCase
import com.intellij.codeInsight.hint.{HintUtil, ShowParameterInfoContext}
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.openapi.fileEditor.{OpenFileDescriptor, FileEditorManager}
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
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

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.10.2009
 */

abstract class CheckPrivateAccessTestBase extends ScalaPsiTestCase {
  val refMarker = "/*ref*/"


  override protected def rootPath = super.rootPath + "checkers/checkPrivateAccess/"

  override protected def doTest {
    import _root_.junit.framework.Assert._
    val filePath = rootPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val scalaFile: ScalaFile = PsiManager.getInstance(myProject).findFile(file).asInstanceOf[ScalaFile]
    val fileText = scalaFile.getText
    val offset = fileText.indexOf(refMarker) + refMarker.length
    assert(offset != refMarker.length - 1, "Not specified caret marker in test case. Use " + refMarker + " in scala file for this.")
    val elem = scalaFile.findElementAt(offset).getParent
    if (!elem.isInstanceOf[ScReferenceElement]) assert(true, "Ref marker should point on reference")
    val ref = elem.asInstanceOf[ScReferenceElement]
    val resolve: PsiMember = PsiTreeUtil.getParentOfType(ref.resolve, classOf[PsiMember], false)


    val res = "" + ResolveUtils.isAccessible(resolve, elem)
    
    println("------------------------ " + scalaFile.getName + " ------------------------")
    println(res)
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
