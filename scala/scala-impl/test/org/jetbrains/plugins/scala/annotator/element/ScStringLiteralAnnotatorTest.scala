package org.jetbrains.plugins.scala.annotator.element

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import junit.framework.Test
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderExtendedMock, Message2}
import org.jetbrains.plugins.scala.base.ScalaFileSetTestCase
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.junit.runner.RunWith
import org.junit.runners.AllTests

@RunWith(classOf[AllTests])
class ScStringLiteralAnnotatorTest
  extends ScalaFileSetTestCase("/annotator/string_literals/") {
  
  override protected def needsSdk: Boolean = true

  override protected def transform(testName: String, fileText: String, project: Project): String = {
    val lightFile = createLightFile(fileText, project)

    val messages = collectMessages(lightFile)

    messages.map(_.textWithRangeAndMessage).mkString("\n")
  }

  private def collectMessages(file: PsiFile): List[Message2] = {
    val mock = new AnnotatorHolderExtendedMock(file)

    val literals = file.depthFirst().filterByType[ScStringLiteral].toSeq
    literals.foreach(ElementAnnotator.annotate(_, typeAware = true)(mock))

    mock.annotations
  }
}

object ScStringLiteralAnnotatorTest {
  def suite: Test = new ScStringLiteralAnnotatorTest
}