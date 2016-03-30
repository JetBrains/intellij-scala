package org.jetbrains.plugins.scala
package lang
package typeConformance

import java.io.File

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.types.Conformance
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypingContext}

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.03.2009
 */

abstract class TypeConformanceTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  protected val caretMarker = "/*caret*/"

  def folderPath: String = baseRootPath() + "typeConformance/"

  protected def doTest(fileText: String, fileName: String = "dummy.scala") = {
    import org.junit.Assert._
    configureFromFileTextAdapter(fileName, fileText.trim)
    val scalaFile = getFileAdapter.asInstanceOf[ScalaFile]
    val caretIndex = scalaFile.getText.indexOf(caretMarker)
    val patternDef =
      if (caretIndex > 0) {
        PsiTreeUtil.findElementOfClassAtOffset(scalaFile, caretIndex, classOf[ScPatternDefinition], false)
      }
      else scalaFile.findLastChildByType[PsiElement](ScalaElementTypes.PATTERN_DEFINITION)
    assert(patternDef != null, "Not specified expression in range to check conformance.")
    val valueDecl = patternDef.asInstanceOf[ScPatternDefinition]
    val declaredType = valueDecl.declaredType.getOrElse(sys.error("Must provide type annotation for LHS"))

    valueDecl.expr.getOrElse(throw new RuntimeException("Expression not found")).getType(TypingContext.empty) match {
      case Success(rhsType, _) =>
        val res: Boolean = Conformance.conforms(declaredType, rhsType)
        val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)
        val text = lastPsi.getText
        val output = lastPsi.getNode.getElementType match {
          case ScalaTokenTypes.tLINE_COMMENT => text.substring(2).trim
          case ScalaTokenTypes.tBLOCK_COMMENT | ScalaTokenTypes.tDOC_COMMENT =>
            text.substring(2, text.length - 2).trim
          case _ => fail("Test result must be in last comment statement")
        }
        val expectedResult = java.lang.Boolean.parseBoolean(output.asInstanceOf[String])
        if (expectedResult != res)
          fail(
            s"""Conformance failure
               |Expected result: $expectedResult
               |declared type: ${declaredType.presentableText}
               |rhs type:      ${rhsType.presentableText}""".stripMargin)
      case Failure(msg, elem) => assert(assertion = false, message = msg + " :: " + elem.get.getText)
    }
  }
  protected def doTest() {
    val filePath = folderPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    doTest(fileText, getTestName(false) + ".scala")
  }
}