package org.jetbrains.plugins.scala.lang.parser.stress

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilderFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.util.containers.ContainerUtil
import junit.framework.Test
import junit.framework.TestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.base.ScalaFileSetTestCase
import org.jetbrains.plugins.scala.lang.parser.ScalaFileSetParserTestCase
import org.junit.Assert

class DragSearchTest extends TestCase

object DragSearchTest {
  private val MAX_ROLLBACKS = 30

  private def exploreForDrags(dragInfo: Array[Pair[TextRange, Integer]]): Unit = {
    val ourMaximum = dragInfo.map(_.getSecond).max
    val result = ContainerUtil.findAll(dragInfo, (pair: Pair[TextRange, Integer]) => pair.getSecond >= MAX_ROLLBACKS)
    val notFound = result.isEmpty
    if (!notFound) {
      Assert.assertTrue(s"Too much rollbacks: $ourMaximum", ourMaximum < MAX_ROLLBACKS)
    }
  }

  def suite: Test = new ScalaFileSetParserTestCase("/parser/stress/data/") {
    override protected def runTest(@NotNull testName: String, @NotNull content: String, @NotNull project: Project): Unit = {
      transform(testName, content, project)
    }

    @NotNull override def transform(@NotNull testName: String, @NotNull fileText: String, @NotNull project: Project): String = {
      val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(getLanguage)
      val psiBuilder = PsiBuilderFactory.getInstance.createBuilder(parserDefinition, parserDefinition.createLexer(project), fileText)
      val dragBuilder = new DragBuilderWrapper(project, psiBuilder)
      parserDefinition.createParser(project).parse(parserDefinition.getFileNodeType, dragBuilder)
      val dragInfo = dragBuilder.getDragInfo
      exploreForDrags(dragInfo)

      super.transform(testName, fileText, project)
    }
  }
}
