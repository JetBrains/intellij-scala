package org.jetbrains.plugins.scala.lang.findUsages

import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.findUsages.factory.{ScalaFindUsagesHandler, ScalaFindUsagesHandlerFactory, ScalaTypeDefinitionFindUsagesOptions}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.util.Markers
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals

import scala.collection.immutable.ListSet

abstract class FindUsagesTestBase extends ScalaFixtureTestCase with Markers {

  protected def defaultOptions = new ScalaTypeDefinitionFindUsagesOptions(getProject)

  protected case class MyUsage(
    navigationRange: TextRange,
    navigationText: String
  )

  protected def doTest(
    fileText: String,
  ): Unit = {
    doTest(fileText, defaultOptions)
  }

  protected def doTest(
    fileText0: String,
    options: FindUsagesOptions
  ): Unit = {
    val fileText = fileText0.withNormalizedSeparator
    val (fileTextClean, expectedUsageRanges) = extractNumberedMarkers(fileText)

    val fileTextWithoutCaret = fileTextClean.replace(CARET, "")
    val expectedUsages = expectedUsageRanges.map { range =>
      val textAtRange = range.substring(fileTextWithoutCaret)
      MyUsage(
        navigationRange = range,
        navigationText = textAtRange,
      )
    }

    doTest(fileTextClean, expectedUsages, options)
  }

  protected def doTest(
    fileText: String,
    expectedUsages: Seq[MyUsage],
    options: FindUsagesOptions
  ): Unit = {
    myFixture.configureByText("dummy.scala", fileText.withNormalizedSeparator)

    val elementAtCaret = myFixture.getElementAtCaret
    val namedElement = PsiTreeUtil.getParentOfType(elementAtCaret, classOf[ScNamedElement], false)

    val actualUsages: Seq[MyUsage] = {
      val result = ListSet.newBuilder[MyUsage]

      val usagesProcessor: Processor[UsageInfo] = usage => {
        val element = usage.getElement
        val navigationRange = usage.getNavigationRange.asInstanceOf[TextRange]
        val navigationText = element.getContainingFile.getText.substring(navigationRange.getStartOffset, navigationRange.getEndOffset)
        result += MyUsage(navigationRange, navigationText)
        true
      }

      val handler = new ScalaFindUsagesHandler(namedElement, ScalaFindUsagesHandlerFactory.getInstance(getProject))
      handler.processElementUsages(namedElement, usagesProcessor, options)

      result.result().toSeq.sortBy(_.navigationRange.getStartOffset)
    }

    assertCollectionEquals(
      "Usages",
      expectedUsages,
      actualUsages
    )
  }
}
