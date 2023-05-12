package org.jetbrains.plugins.scala.annotator.element

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import junit.framework.Test
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderWithRangeMock, MessageWithRange}
import org.jetbrains.plugins.scala.base.ScalaFileSetTestCase
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.junit.runner.RunWith
import org.junit.runners.AllTests

@RunWith(classOf[AllTests])
class ScStringLiteralAnnotatorTest
  extends ScalaFileSetTestCase("/annotator/string_literals/") {

  override protected def transform(testName: String, fileText: String, project: Project): String = {
    val lightFile = createLightFile(fileText, project)

    val messages = collectMessages(lightFile)

    messages.map(_.textWithoutCode).mkString("\n")
  }

  private def collectMessages(file: PsiFile): List[MessageWithRange] = {
    val mock = new AnnotatorHolderWithRangeMock(file)

    val literals = file.depthFirst().filterByType[ScStringLiteral].toSeq
    literals.foreach(ElementAnnotator.annotate(_, typeAware = true)(mock))

    mock.annotations
  }
}

object ScStringLiteralAnnotatorTest {
  def suite: Test = new ScStringLiteralAnnotatorTest
}