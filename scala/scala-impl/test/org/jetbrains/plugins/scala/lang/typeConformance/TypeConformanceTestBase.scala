package org.jetbrains.plugins.scala
package lang
package typeConformance

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiComment, PsiElement}
import org.jetbrains.plugins.scala.base.{FailableTest, ScalaLightCodeInsightFixtureTestAdapter, SharedTestProjectToken}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt, TypePresentationContext}
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert.fail
import org.junit.experimental.categories.Category

import java.io.File

/**
  * User: Alexander Podkhalyuzin
  * Date: 10.03.2009
  */
@Category(Array(classOf[TypecheckerTests]))
abstract class TypeConformanceTestBase extends ScalaLightCodeInsightFixtureTestAdapter with FailableTest {
  protected val caretMarker = "/*caret*/"

  def folderPath: String = TestUtils.getTestDataPath + "/typeConformance/"

  override protected def sharedProjectToken = SharedTestProjectToken(this.getClass)

  protected def doTest(fileText: String, fileName: String = getTestName(false) + ".scala", checkEquivalence: Boolean = false): Unit = {
    configureFromFileText(fileText.trim, ScalaFileType.INSTANCE)
    doTestInner(checkEquivalence)
  }

  private def errorMessage(title: String, expected: Boolean, declaredType: ScType, rhsType: ScType)(implicit tpc: TypePresentationContext) = {
    s"""$title
       |Expected result: $expected
       |declared type:   ${declaredType.presentableText}
       |rhs type:        ${rhsType.presentableText}""".stripMargin
  }

  private def doTestInner(checkEquivalence: Boolean): Unit = {
    implicit val tpc: TypePresentationContext = TypePresentationContext.emptyContext
    val (declaredType, rhsType) = declaredAndExpressionTypes()
    val expected = expectedResult
    if (checkEquivalence) {
      val equiv1 = rhsType.equiv(declaredType)
      val equiv2 = declaredType.equiv(rhsType)
      if (equiv1 != expected || equiv2 != expected) {
        if (!shouldPass) return
        fail(errorMessage("Equivalence failure", expected, declaredType, rhsType))
      }

      if (expected) {
        val conforms = rhsType.conforms(declaredType)
        if (!conforms) {
          if (!shouldPass) return
          fail(errorMessage("Conformance failure", expected, declaredType, rhsType))
        }
      }
    }
    else {
      val res: Boolean = rhsType.conforms(declaredType)
      if (expected != res) {
        if (!shouldPass) return
        fail(errorMessage("Conformance failure", expected, declaredType, rhsType))
      }
    }
    if (!shouldPass) fail(failingPassed)
  }

  protected def doTest(): Unit = {
    doTest(false)
  }

  protected def doTest(checkEquivalence: Boolean): Unit = {
    configureFromFile()
    doTestInner(checkEquivalence)
  }

  protected def configureFromFile(fileName: String = getTestName(false) + ".scala"): Unit = {
    val filePath = folderPath + fileName
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    configureFromFileText(fileText.trim, ScalaFileType.INSTANCE)
  }

  protected def declaredAndExpressionTypes(): (ScType, ScType) = {
    val scalaFile = getFile.asInstanceOf[ScalaFile]
    val caretIndex = scalaFile.getText.indexOf(caretMarker)
    val patternDef =
      if (caretIndex > 0) {
        PsiTreeUtil.findElementOfClassAtOffset(scalaFile, caretIndex, classOf[ScPatternDefinition], false)
      }
      else scalaFile.findLastChildByTypeScala[PsiElement](ScalaElementType.PATTERN_DEFINITION).orNull
    assert(patternDef != null, "Not specified expression in range to check conformance.")
    val valueDecl = patternDef.asInstanceOf[ScPatternDefinition]
    val declaredType = valueDecl.declaredType.getOrElse(sys.error("Must provide type annotation for LHS"))

    val expr = valueDecl.expr.getOrElse(sys.error("Expression not found"))
    expr.`type`() match {
      case Right(rhsType) => (declaredType, rhsType)
      case Failure(msg) => sys.error(s"Couldn't compute type of ${expr.getText}: $msg")
    }
  }

  private def expectedResult: Boolean = {
    val scalaFile = getFile.asInstanceOf[ScalaFile]
    val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)
    val text = lastPsi.getText
    val output = lastPsi.getNode.getElementType match {
      case ScalaTokenTypes.tLINE_COMMENT => text.substring(2).trim
      case ScalaTokenTypes.tBLOCK_COMMENT | ScalaTokenTypes.tDOC_COMMENT =>
        text.substring(2, text.length - 2).trim
      case _ => fail("Test result must be in last comment statement")
    }
    val expectedResult = java.lang.Boolean.parseBoolean(output.asInstanceOf[String])
    expectedResult
  }

  def doApplicationConformanceTest(fileText: String, fileName: String = "dummy.scala"): Unit = {
    import org.junit.Assert._
    configureFromFileText(fileText.trim, ScalaFileType.INSTANCE)
    val scalaFile = getFile.asInstanceOf[ScalaFile]
    val caretIndex = scalaFile.getText.indexOf(caretMarker)
    val element = if (caretIndex > 0) {
      PsiTreeUtil.findElementOfClassAtOffset(scalaFile, caretIndex, classOf[ScMethodCall], false)
    }
    else scalaFile.findLastChildByTypeScala[PsiElement](ScalaElementType.METHOD_CALL).orNull
    assertNotNull("Failed to locate application",element)
    val application = element.asInstanceOf[ScMethodCall]
    val errors = scala.collection.mutable.ArrayBuffer[String]()
    val expectedResult = scalaFile.findElementAt(scalaFile.getText.length - 1) match {
      case c: PsiComment => c.getText
      case _ => "True"
    }
    for ((expr, param) <- application.matchedParameters) {
      val exprTp = expr.`type`().getOrElse(throw new RuntimeException(s"Failed to get type of expression(${expr.getText})"))
      val res = exprTp.conforms(param.paramType)
      if (res != expectedResult.toBoolean)
        errors +=
          s"""
             |Expected: $expectedResult
             |Param tp: ${param.paramType.presentableText(TypePresentationContext.emptyContext)}
             |Arg   tp: ${exprTp.presentableText(TypePresentationContext.emptyContext)}
          """.stripMargin
    }
    assertTrue(if (shouldPass) "Conformance failure:\n"+ errors.mkString("\n\n").trim else failingPassed, !shouldPass ^ errors.isEmpty)
  }
}