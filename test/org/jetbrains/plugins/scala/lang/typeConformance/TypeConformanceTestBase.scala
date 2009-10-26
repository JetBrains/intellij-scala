package org.jetbrains.plugins.scala
package lang
package typeConformance

import base.ScalaPsiTestCase
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiComment, PsiManager}
import java.io.File
import java.lang.String
import lexer.ScalaTokenTypes
import parser.ScalaElementTypes
import psi.api.expr.ScExpression
import psi.api.ScalaFile
import psi.api.statements.{ScValueDeclaration, ScValue, ScPatternDefinition}
import psi.types.{Conformance, ScType}
import psi.types.result.{TypingContext, Failure, Success}

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.03.2009
 */

abstract class TypeConformanceTestBase extends ScalaPsiTestCase {
  private val startExprMarker = "/*start*/"
  private val endExprMarker = "/*end*/"

  override def rootPath: String = super.rootPath + "typeConformance/"

  protected def doTest = {
    import _root_.junit.framework.Assert._

    val filePath = rootPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val scalaFile: ScalaFile = PsiManager.getInstance(myProject).findFile(file).asInstanceOf[ScalaFile]
    val expr: PsiElement = scalaFile.findLastChildByType(ScalaElementTypes.PATTERN_DEFINITION)
    assert(expr != null, "Not specified expression in range to check conformance.")
    val valueDecl = expr.asInstanceOf[ScPatternDefinition]
    val declaredType = valueDecl.declaredType.getOrElse(Predef.error("Must provide type annotation for LHS"))

    valueDecl.expr.getType(TypingContext.empty) match {
      case Success(rhsType, _) => {
        val res: Boolean = Conformance.conforms(declaredType, rhsType)
        println("------------------------ " + scalaFile.getName + " ------------------------")
        println(res)
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
      case Failure(msg, elem) => assert(false, msg + " :: " + elem.get.getText)
    }
  }
}