package org.jetbrains.plugins.scala.uast

import com.intellij.platform.uast.testFramework.common.RenderLogTestBase
import com.intellij.psi.PsiElement
import com.intellij.testFramework.EqualsToFile

import java.io.File
import org.jetbrains.uast._
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.junit.Assert

class SimpleScalaRenderingLogTest
    extends AbstractUastFixtureTest
    with RenderLogTestBase {

  def testSimpleClass(): Unit = doTest()

  def testClassWithInners(): Unit = doTest()

  def testAnnotations(): Unit = doTest()

  def testAnnotationComplex(): Unit = doTest()

  def testAnnotationParameters(): Unit = doTest()

  def testClassAnnotation(): Unit = doTest()

  def testDefaultImpls(): Unit = doTest()

  def testDefaultParameterValues(): Unit = doTest()

  def testIfStatement(): Unit = doTest()

  def testImports(): Unit = doTest()

  def testLocalVariableWithAnnotation(): Unit = doTest()

  def testParametersDisorder(): Unit = doTest()

  def testQualifiedConstructorCall(): Unit = doTest()

  def testTypeReferences(): Unit = doTest()

  def testAnonymous(): Unit = doTest()

  def testCallExpressions(): Unit = doTest()

  def testMatch(): Unit = doTest()

  def testLocalFunctions(): Unit = doTest()

  def testLambdas(): Unit = doTest()

  private def doComplexTest(): Unit = doTest("complex/" + getTestName(false))

  def testComplexSample1(): Unit = doComplexTest()

  def testComplexSample2(): Unit = doComplexTest()

  def testComplexSample3(): Unit = doComplexTest()

  private def getTestFile(testName: String, ext: String) = {

    def substringBeforeLast(str: String, delimiter: Char): String = {
      val index = str.lastIndexOf(delimiter)
      if (index == -1) str else str.substring(0, index)
    }

    new File(getTestDataPath(), substringBeforeLast(testName, '.') + '.' + ext)
  }

  override def check(testName: String,
                     file: UFile,
                     doParentConsistencyCheck: Boolean): Unit = {
    val renderFile = getTestFile(testName, "render.txt")
    val logFile = getTestFile(testName, "log.txt")

    EqualsToFile.assertEqualsToFile(
      "Render string",
      renderFile,
      file.asRenderString()
    )
    EqualsToFile.assertEqualsToFile(
      "Log string",
      logFile,
      UastUtils.asRecursiveLogString(file)
    )

    if (doParentConsistencyCheck) {
      checkParentConsistency(file)
    }

    checkContainingFileForAllElements(file)
  }

  override def checkContainingFileForAllElements(uFile: UFile): Unit =
    uFile.accept(new AbstractUastVisitor {
      override def visitElement(node: UElement): Boolean = {
        if (node.isInstanceOf[PsiElement] && node.getSourcePsi != null) {
          val uElement = UastContextKt.toUElement(node.getSourcePsi)
          Assert.assertEquals(
            s"getContainingUFile should be equal to source for ${uElement.getClass}",
            uFile,
            UastUtils.getContainingUFile(uElement)
          )
        }

        node match {
          case declaration: UDeclaration if declaration.getUastAnchor != null =>
            val uastAnchor = declaration.getUastAnchor
            Assert.assertEquals(
              s"should be appropriate sourcePsi for uastAnchor for ${node.getClass} [${node.getSourcePsi}] ",
              Option(node.getSourcePsi)
                .flatMap(s => Option(s.getContainingFile))
                .orNull,
              Option(uastAnchor.getSourcePsi)
                .flatMap(s => Option(s.getContainingFile))
                .orNull,
            )
          case _ =>
        }

        val javaPsi = node.getJavaPsi
        if (javaPsi.isInstanceOf[UElement]) {
          Assert.fail(s"javaPsi: ${javaPsi.getClass} for ${node.getClass} should not be a `UElement`")
        }

        false
      }
    })
}
