package org.jetbrains.plugins.scala.uast

import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._

import java.io.File
import org.jetbrains.uast._
import org.jetbrains.uast.visitor.AbstractUastVisitor

import scala.collection.mutable
import scala.reflect.ClassTag

abstract class AbstractUastFixtureTest
  extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  override def runInDispatchThread() = false

  override def getTestDataPath: String = super.getTestDataPath + "uast"

  protected def getTestFile(testName: String): File =
    new File(getTestDataPath, testName + ".scala")

  protected def check(testName: String, file: UFile): Unit

  protected def doTest(
    testName: String = getTestName(false),
    checkCallback: (String, UFile) => Unit = check
  ): Unit = {
    val testFile = getTestFile(testName)
    if (!testFile.exists())
      throw new IllegalStateException(s"File does not exist: $testFile")
    val psiFile = myFixture.configureByFile(testFile.getPath)
    inReadAction {
      psiFile.convertWithParentTo[UElement]() match {
        case Some(uFile: UFile) => checkCallback(testName, uFile)
        case _ =>
          throw new IllegalStateException(s"Can't get UFile for $testName")
      }
    }
  }
}

object AbstractUastFixtureTest {
  def findElementByText[T: ClassTag](elem: UElement, refText: String): T = {
    val matchingElements = mutable.ArrayBuffer.empty[T]
    elem.accept(new AbstractUastVisitor {
      override def visitElement(node: UElement): Boolean = {
        node match {
          case e: T if Option(node.getSourcePsi).exists(_.textMatches(refText)) =>
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

    val nonStrictParentElements = LazyList
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
    if (strict && uElemSourcePsi != null && !uElemSourcePsi.textMatches(refText))
      throw new AssertionError(
        s"requested text '$refText' found as '${uElemSourcePsi.getText}' in $uElemContainingText"
      )

    uElemContainingText
  }
}
