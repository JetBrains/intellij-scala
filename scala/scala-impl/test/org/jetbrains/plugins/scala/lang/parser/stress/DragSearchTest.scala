package org.jetbrains.plugins.scala.lang.parser.stress

import com.intellij.lang.{LanguageParserDefinitions, PsiBuilderFactory}
import com.intellij.openapi.util.{Pair, TextRange}
import org.jetbrains.plugins.scala.base.{DefaultFileSetTestTransform, NoSdkFileSetTestBase}
import org.junit.Assert.assertTrue

import java.nio.file.Path

class DragSearchTest extends NoSdkFileSetTestBase with DefaultFileSetTestTransform {
  override protected def relativeTestDataPath: Path = Path.of("parser", "stress", "data")

  override protected def runTest(testName: String, fileText: String): Unit = {
    transform(testName, fileText)
  }

  override protected def transform(testName: String, fileText: String): String = {
    val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language)
    val psiBuilder = PsiBuilderFactory.getInstance().createBuilder(
      parserDefinition,
      parserDefinition.createLexer(project),
      fileText
    )
    val dragBuilder = new DragBuilderWrapper(project, psiBuilder)
    parserDefinition.createParser(project).parse(parserDefinition.getFileNodeType(), dragBuilder)

    val dragInfo = dragBuilder.getDragInfo.map(p2p)
    exploreForDrags(dragInfo)

    super.transform(testName, fileText)
  }

  private final val MaxRollbacks = 30

  private def exploreForDrags(dragInfo: Array[(TextRange, Int)]): Unit = {
    val ourMaximum = dragInfo.maxBy(_._2)._2
    val notFound = !dragInfo.exists(_._2 >= MaxRollbacks)

    if (!notFound) {
      assertTrue("Too many rollbacks: " + ourMaximum, ourMaximum < MaxRollbacks)
    }
  }

  private def p2p(pair: Pair[TextRange, Integer]): (TextRange, Int) = (pair.getFirst, pair.getSecond)
}
