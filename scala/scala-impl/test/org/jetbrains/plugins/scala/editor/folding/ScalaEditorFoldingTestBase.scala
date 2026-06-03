package org.jetbrains.plugins.scala.editor.folding

import com.intellij.codeInsight.folding.CodeFoldingSettings
import com.intellij.openapi.util.TextRange
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.editor.folding.ScalaEditorFoldingTestBase.FoldingInfo
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.folding.ScalaFoldingBuilder
import org.jetbrains.plugins.scala.settings.ScalaCodeFoldingSettings
import org.jetbrains.plugins.scala.util.RevertableChange
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.junit.Assert.assertFalse

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

abstract class ScalaEditorFoldingTestBase extends ScalaLightCodeInsightFixtureTestCase {
  private val FOLD_START_MARKER_BEGIN = "<|fold["
  private val FOLD_START_MARKER_END = "]>"
  private val FOLD_END_MARKER = "</fold>"

  // Example: "<|fold[{...}]><collapseByDefault/>some code...</fold>"
  // It would be better to implement this as an attribute of some special fold tag
  // For simplicity, it  would require rewriting the placeholder part (which currently goes in [...]) with attributes as well:
  // <fold placeholder="placeholder text" collapsedByDefault=true>...</fold>
  // maybe some day ...
  protected val COLLAPSED_BY_DEFAULT_MARKER = "<collapseByDefault/>"

  def ST(text: String) =
    FOLD_START_MARKER_BEGIN + text + FOLD_START_MARKER_END

  override val END = FOLD_END_MARKER

  val BLOCK_ST = ST("{...}")
  val PAR_ST = ST("(...)")
  val DOTS_ST = ST("...")
  val COMMENT_ST = ST("/.../")
  val DOC_COMMENT_ST = ST("/**...*/")
  val MLS_ST = ST("\"\"\"...\"\"\"")

  protected val INDENT_REGION = ST(" ...")

  protected final def checkNoFoldingRegions(fileTextRaw: String): Unit = {
    assertFalse("text shouldn't contain expected folding", fileTextRaw.contains(FOLD_END_MARKER))
    genericCheckRegions(fileTextRaw)
  }

  protected final def genericCheckRegions(fileTextRaw: String): Unit =
    genericCheckRegions(fileTextRaw, sortFoldings = false)

  /** @param sortFoldings whether to sort folding regions when comparing expected and actual foldings.<br>
   *               The order in which folding regions are added in platform sometimes can be different
   *               from the order of extraction of expected folding regions from the test data. This is mostly the case
   *               when there are some overlapping regions with the same start offset. */
  protected def genericCheckRegions(fileTextRaw: String, sortFoldings: Boolean): Unit = {
    val fileText = fileTextRaw.withNormalizedSeparator
    val expectedRegions = new ArrayBuffer[FoldingInfo]()
    val textWithoutMarkers = new mutable.StringBuilder(fileText.length)
    val openMarkers = mutable.Stack[(Int, String, Boolean)]()

    var pos = 0
    while ({
      val nextStartMarker = fileText.indexOf(FOLD_START_MARKER_BEGIN, pos)
      val nextEndMarker = fileText.indexOf(FOLD_END_MARKER, pos)

      val magic = Seq(nextStartMarker -> true, nextEndMarker -> false)
        .filter(_._1 >= 0)
        .sortBy(_._1)
      magic match {
        case (closestIdx, isStartMarker) +: _ =>
          textWithoutMarkers.append(fileText.substring(pos, closestIdx))
          val idxInTargetFile = textWithoutMarkers.length

          if (isStartMarker) {
            val replacementTextBeginIdx = closestIdx + FOLD_START_MARKER_BEGIN.length
            val replacementTextEndIdx = fileText.indexOf(FOLD_START_MARKER_END, replacementTextBeginIdx)
            assert(replacementTextEndIdx >= 0, "Expected end of start marker " + replacementTextBeginIdx)
            val replacementText = fileText.substring(replacementTextBeginIdx, replacementTextEndIdx)
            pos = replacementTextEndIdx + FOLD_START_MARKER_END.length

            val isCollapsedByDefault = fileText.view.drop(pos).startsWith(COLLAPSED_BY_DEFAULT_MARKER)
            if (isCollapsedByDefault)
              pos += COLLAPSED_BY_DEFAULT_MARKER.length

            openMarkers.push((idxInTargetFile, replacementText, isCollapsedByDefault))
          } else {
            assert(openMarkers.nonEmpty, "No more open markers for end marker at " + closestIdx)
            val (regionBegin, replacementText, isCollapsedByDefault) = openMarkers.pop()
            val regionEnd = idxInTargetFile
            pos = closestIdx + FOLD_END_MARKER.length
            expectedRegions += FoldingInfo(
              TextRange.create(regionBegin, regionEnd),
              replacementText,
              isCollapsedByDefault
            )
          }
          true
        case Seq() =>
          textWithoutMarkers.append(fileText.substring(pos, fileText.length))
          false
      }
    }) ()

    assert(openMarkers.isEmpty, s"Unbalanced fold markers #3: ${openMarkers.mkString}")

    val assumedRegionRanges = expectedRegions.sortBy(_.range.getStartOffset)

    myFixture.configureByText("dummy.scala", textWithoutMarkers.result())

    val myBuilder = new ScalaFoldingBuilder
    val regions = myBuilder.buildFoldRegions(myFixture.getFile.getNode, myFixture getDocument myFixture.getFile)

    val actualFoldingInfos = regions.map(region => FoldingInfo(
      region.getRange,
      // call `getPlaceholderText` instead of `getLanguagePlaceholderText` to get custom foldings as well
      myBuilder.getPlaceholderText(region.getElement, region.getRange),
      // using builder instead of `region.isCollapsedByDefault` because latest returns null by default
      // when regions are obtained from `myBuilder.buildFoldRegions`
      myBuilder.isCollapsedByDefault(region)
    ))

    val expected0 = assumedRegionRanges.toList
    val actual0 = actualFoldingInfos.toList
    import FoldingInfo.orderByRangeAndPlaceholder
    val expected = if (sortFoldings) expected0.sorted else expected0
    val actual = if (sortFoldings) actual0.sorted else actual0
    assertCollectionEquals("Folding regions do not match", expected, actual)
  }


  protected class WithModifiedSettings[BeanType](beanInstanceGetter: () => BeanType) extends RevertableChange {
    private var settingsBefore: BeanType = _
    private lazy val settings : BeanType = beanInstanceGetter().ensuring(_ != null)

    override def applyChange(): Unit = {
      settingsBefore = XmlSerializerUtil.createCopy(settings)
    }

    override def revertChange(): Unit =
      XmlSerializerUtil.copyBean(settingsBefore, settings)

    final def run(body: BeanType => Unit): Unit = {
      this.applyChange()
      try
        body(settings)
      finally
        this.revertChange()
    }
  }

  protected def runWithModifiedScalaFoldingSettings(body: ScalaCodeFoldingSettings => Unit): Unit =
    new WithModifiedSettings(() => ScalaCodeFoldingSettings.getInstance()).run(body)

  protected def runWithModifiedFoldingSettings(body: CodeFoldingSettings => Unit): Unit =
    new WithModifiedSettings(() => CodeFoldingSettings.getInstance()).run(body)
}

object ScalaEditorFoldingTestBase {

  private case class FoldingInfo(range: TextRange, placeholder: String, isCollapsedByDefault: Boolean)

  private object FoldingInfo {
    implicit lazy val orderByRangeAndPlaceholder: Ordering[FoldingInfo] = Ordering.by(f => (f.range, f.placeholder))

    implicit lazy val textRangeOrdering: Ordering[TextRange]   = Ordering.by(r => (r.getStartOffset, r.getEndOffset))
  }
}
