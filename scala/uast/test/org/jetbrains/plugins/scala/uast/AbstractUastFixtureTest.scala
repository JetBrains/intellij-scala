package org.jetbrains.plugins.scala
package uast

import java.io.File

import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.psi.uast.converter.BaseScala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.utils.OptionExt._
import org.jetbrains.uast._
import org.jetbrains.uast.test.common.RenderLogTestBase
import org.jetbrains.uast.test.env.AbstractTestWithCoreEnvironmentKt
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.junit.Assert

import scala.collection.mutable
import scala.reflect.ClassTag

abstract class AbstractUastFixtureTest
  extends ScalaLightCodeInsightFixtureTestAdapter {

  override def getTestDataPath: String = super.getTestDataPath + "uast"

  def getTestFile(testName: String): File = new File(getTestDataPath, testName)

  def check(testName: String, file: UFile)

  def doTest(testName: String)(checkCallback: (String, UFile) => Unit = check) {
    val testFile = getTestFile(testName)
    if (!testFile.exists())
      throw new IllegalStateException(s"File does not exist: $testFile")
    val psiFile = myFixture.configureByFile(testFile.getPath)
    psiFile.convertWithParentTo[UElement]() match {
      case Some(uFile: UFile) => checkCallback(testName, uFile)
      case _ =>
        throw new IllegalStateException(s"Can't get UFile for $testName")
    }
  }
}

object AbstractUastFixtureTest {
  def findElementByText[T: ClassTag](elem: UElement, refText: String): T = {
    val matchingElements = mutable.ArrayBuffer.empty[T]
    elem.accept(new AbstractUastVisitor {
      override def visitElement(node: UElement): Boolean = {
        node match {
          case e: T if Option(node.getSourcePsi).exists(_.getText == refText) =>
            matchingElements += e; false
          case _ => false
        }
      }
    })

    matchingElements.size match {
      case 0 =>
        throw new IllegalArgumentException(s"Reference '$refText' not found")
      case 1 => matchingElements(0)
      case _ =>
        throw new IllegalArgumentException(s"Reference '$refText' is ambiguous")
    }
  }

  def findElementByTextFromPsi[T >: Null <: UElement: ClassTag](
    uElem: UElement,
    refText: String,
    strict: Boolean = true
  ): T = {
    if (uElem.getSourcePsi == null)
      throw new AssertionError(s"no sourcePsi for $uElem")
    findUElementByTextFromPsi[T](uElem.getSourcePsi, refText, strict)
  }

  def findUElementByTextFromPsi[T >: Null <: UElement: ClassTag](
    psiElem: PsiElement,
    refText: String,
    strict: Boolean = true
  ): T = {
    val elementAtStart = psiElem.findElementAt(psiElem.getText.indexOf(refText))
    if (elementAtStart == null)
      throw new AssertionError(
        s"requested text '$refText' was not found in $psiElem"
      )

    val nonStrictParentElements = Stream
      .iterate(elementAtStart)(
        it =>
          if (it.isInstanceOf[PsiFile]) null
          else it.getParent
      )
      .takeWhile(_ != null)

    val parentElements =
      if (strict)
        nonStrictParentElements.dropWhile(!_.getText.contains(refText))
      else nonStrictParentElements

    val uElemContainingText = parentElements
      .flatMap(_.convertWithParentTo[T]())
      .headOption
      .getOrElse(
        throw new AssertionError(
          s"requested text '$refText' not found as ${implicitly[ClassTag[T]]}"
        )
      )

    val uElemSourcePsi = uElemContainingText.getSourcePsi
    if (strict && uElemSourcePsi != null && uElemSourcePsi.getText != refText)
      throw new AssertionError(
        s"requested text '$refText' found as '${uElemSourcePsi.getText}' in $uElemContainingText"
      )

    uElemContainingText
  }
}

abstract class AbstractScalaRenderLogTest
    extends AbstractUastFixtureTest
    with RenderLogTestBase {

  protected def getTestFile(testName: String, ext: String) = {

    def substringBeforeLast(str: String, delimiter: Char): String = {
      val index = str.lastIndexOf(delimiter)
      if (index == -1) str else str.substring(0, index)
    }

    new File(getTestDataPath(), substringBeforeLast(testName, '.') + '.' + ext)
  }

  override def check(s: String, uFile: UFile): Unit =
    RenderLogTestBase.DefaultImpls.check(this, s, uFile)

  override def check(testName: String,
                     file: UFile,
                     doParentConsistencyCheck: Boolean) {
    val renderFile = getTestFile(testName, "render.txt")
    val logFile = getTestFile(testName, "log.txt")

    AbstractTestWithCoreEnvironmentKt.assertEqualsToFile(
      "Render string",
      renderFile,
      file.asRenderString()
    )
    AbstractTestWithCoreEnvironmentKt.assertEqualsToFile(
      "Log string",
      logFile,
      UastUtils.asRecursiveLogString(file)
    )

    if (doParentConsistencyCheck) {
      checkParentConsistency(file)
    }

    checkContainingFileForAllElements(file)
  }

  override def checkParentConsistency(uFile: UFile): Unit =
    RenderLogTestBase.DefaultImpls.checkParentConsistency(this, uFile)

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
              node.getSourcePsi ?> (_.getContainingFile) orNull,
              uastAnchor.getSourcePsi ?> (_.getContainingFile) orNull
            )
          case _ =>
        }

        false
      }
    })
}
