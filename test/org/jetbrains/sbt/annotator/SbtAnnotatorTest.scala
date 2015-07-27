package org.jetbrains.sbt.annotator

import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator._
import org.jetbrains.sbt.SbtBundle
import junit.framework.Assert.assertTrue


/**
 * @author Nikolay Obedin
 * @since 7/23/15.
 */

class SbtAnnotatorMock(sbtVersion: String) extends SbtAnnotator {
  override def getSbtVersion(element: PsiElement): String = sbtVersion
}

abstract class SbtAnnotatorTestBase extends AnnotatorTestBase {
  protected def annotator: Annotator

  protected def doTest(messages: Seq[Message]) {
    val mock = new AnnotatorHolderMock
    annotator.annotate(loadTestFile(), mock)
    assertTrue(messages.forall(mock.annotations.contains))
    assertTrue(mock.annotations.forall(messages.contains))
  }
}


class SbtAnnotatorTest012 extends SbtAnnotatorTestBase {
  override def annotator = new SbtAnnotatorMock("0.12.4")

  def testSbtAnnotator =
    doTest(Seq(
      Error("version := \"SNAPSHOT\"", SbtBundle("sbt.annotation.blankLineRequired", "0.12.4")),
      Error("lazy val foo = project.in(file(\"foo\"))", SbtBundle("sbt.annotation.sbtFileMustContainOnlyExpressions"))
    ))
}

class SbtAnnotatorTest013 extends SbtAnnotatorTestBase {
  override def annotator = new SbtAnnotatorMock("0.13.1")

  def testSbtAnnotator =
    doTest(Seq(
      Error("version := \"SNAPSHOT\"", SbtBundle("sbt.annotation.blankLineRequired", "0.13.1"))
    ))
}

class SbtAnnotatorTest0137 extends SbtAnnotatorTestBase {
  override def annotator = new SbtAnnotatorMock("0.13.7")

  def testSbtAnnotator =
    doTest(Seq.empty)
}

class SbtAnnotatorTestNullVersion extends SbtAnnotatorTestBase {
  override def annotator = new SbtAnnotatorMock(null)

  def testSbtAnnotator =
    doTest(Seq.empty)
}
