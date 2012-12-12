package org.jetbrains.plugins.scala
package lang
package typeConformance

import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.psi.PsiElement
import java.io.File
import java.lang.String
import lexer.ScalaTokenTypes
import parser.ScalaElementTypes
import psi.api.ScalaFile
import psi.api.statements.ScPatternDefinition
import psi.types.Conformance
import psi.types.result.{TypingContext, Failure, Success}
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.io.FileUtil
import base.ScalaLightPlatformCodeInsightTestCaseAdapter

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.03.2009
 */

abstract class TypeConformanceTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  def folderPath: String = baseRootPath() + "typeConformance/"

  protected def doTest() {
    import _root_.junit.framework.Assert._

    val filePath = folderPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    configureFromFileTextAdapter(getTestName(false) + ".scala", fileText)
    val scalaFile = getFileAdapter.asInstanceOf[ScalaFile]
    val expr: PsiElement = scalaFile.findLastChildByType(ScalaElementTypes.PATTERN_DEFINITION)
    assert(expr != null, "Not specified expression in range to check conformance.")
    val valueDecl = expr.asInstanceOf[ScPatternDefinition]
    val declaredType = valueDecl.declaredType.getOrElse(scala.Predef.error("Must provide type annotation for LHS"))

    valueDecl.expr.getOrElse(throw new RuntimeException("Expression not found")).getType(TypingContext.empty) match {
      case Success(rhsType, _) => {
        val res: Boolean = Conformance.conforms(declaredType, rhsType)
        val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)
        val text = lastPsi.getText
        val output = lastPsi.getNode.getElementType match {
          case ScalaTokenTypes.tLINE_COMMENT => text.substring(2).trim
          case ScalaTokenTypes.tBLOCK_COMMENT | ScalaTokenTypes.tDOC_COMMENT =>
            text.substring(2, text.length - 2).trim
          case _ => fail("Test result must be in last comment statement")
        }
        if (java.lang.Boolean.parseBoolean(output.asInstanceOf[String]) != res) fail("conformance wrong")
      }
      case Failure(msg, elem) => assert(assertion = false, message = msg + " :: " + elem.get.getText)
    }
  }
}