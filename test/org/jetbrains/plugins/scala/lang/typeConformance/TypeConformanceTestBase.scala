package org.jetbrains.plugins.scala
package lang
package typeConformance

import java.io.File

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypingContext}
import org.jetbrains.plugins.scala.project.ProjectExt

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.03.2009
 */

abstract class TypeConformanceTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  def folderPath: String = baseRootPath() + "typeConformance/"

  protected def doTest(fileText: String, fileName: String = "dummy.scala") = {
    import org.junit.Assert._
    configureFromFileTextAdapter(fileName, fileText.trim)
    val scalaFile = getFileAdapter.asInstanceOf[ScalaFile]
    val expr: PsiElement = scalaFile.findLastChildByType[PsiElement](ScalaElementTypes.PATTERN_DEFINITION)
    assert(expr != null, "Not specified expression in range to check conformance.")
    val valueDecl = expr.asInstanceOf[ScPatternDefinition]
    val declaredType = valueDecl.declaredType.getOrElse(sys.error("Must provide type annotation for LHS"))

    valueDecl.expr.getOrElse(throw new RuntimeException("Expression not found")).getType(TypingContext.empty) match {
      case Success(rhsType, _) =>
        val res: Boolean = rhsType.conforms(declaredType)(expr.getProject.typeSystem)
        val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)
        val text = lastPsi.getText
        val output = lastPsi.getNode.getElementType match {
          case ScalaTokenTypes.tLINE_COMMENT => text.substring(2).trim
          case ScalaTokenTypes.tBLOCK_COMMENT | ScalaTokenTypes.tDOC_COMMENT =>
            text.substring(2, text.length - 2).trim
          case _ => fail("Test result must be in last comment statement")
        }
        if (java.lang.Boolean.parseBoolean(output.asInstanceOf[String]) != res) fail(s"Conformance failure")
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