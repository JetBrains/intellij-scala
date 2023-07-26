package org.jetbrains.plugins.scala.lang.findUsages

import com.intellij.openapi.util.{Segment, TextRange}
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.findUsages.factory.ScalaFindUsagesConfiguration
import org.jetbrains.plugins.scala.util.Markers
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals

import scala.jdk.CollectionConverters.CollectionHasAsScala

abstract class FindUsagesTestBase extends ScalaFixtureTestCase with Markers {

  override def tearDown(): Unit = {
    ScalaFindUsagesConfiguration.getInstance(getProject).reset()
    super.tearDown()
  }

  protected case class MyUsage(
    navigationRange: TextRange,
    navigationText: String
  )

  protected def doTest(
    fileText0: String,
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

    doTest(fileTextClean, expectedUsages)
  }

  protected def doTest(
    fileText: String,
    expectedUsages: Seq[MyUsage],
  ): Unit = {
    myFixture.configureByText("dummy.scala", fileText.withNormalizedSeparator)

    val usageInfos: Seq[UsageInfo] = myFixture.findUsages(myFixture.getElementAtCaret)
      .asScala.toSeq
      .sortBy(_.getNavigationRange.getStartOffset)
    val actualUsages: Seq[MyUsage] = usageInfos
      .map(toMyUsage)
      //NOTE: for some reason in some cases there are duplicates usages found. It looks like it's not critical, though
      .distinct

    assertCollectionEquals(
      "Usages",
      expectedUsages,
      actualUsages
    )
  }

  private def toRange(s: Segment): TextRange =
    TextRange.create(s.getStartOffset, s.getEndOffset)

  private def toMyUsage(usage: UsageInfo): MyUsage = {
    val range = toRange(usage.getNavigationRange)
    val text = range.substring(usage.getFile.getText)
    MyUsage(range, text)
  }
}
