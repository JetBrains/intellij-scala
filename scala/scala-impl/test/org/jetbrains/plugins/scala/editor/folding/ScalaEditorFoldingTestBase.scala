package org.jetbrains.plugins.scala.editor.folding

import com.intellij.codeInsight.folding.CodeFoldingSettings
import com.intellij.openapi.util.TextRange
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestAdapter, SharedTestProjectToken}
import org.jetbrains.plugins.scala.compilation.CompilerTestUtil.RevertableChange
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.folding.ScalaFoldingBuilder
import org.jetbrains.plugins.scala.settings.ScalaCodeFoldingSettings
import org.junit.Assert.assertEquals

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

abstract class ScalaEditorFoldingTestBase extends ScalaLightCodeInsightFixtureTestAdapter {
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

  override protected def sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken(this.getClass)

  def genericCheckRegions(fileTextRaw: String): Unit = {
    val fileText = fileTextRaw.withNormalizedSeparator
    val expectedRegions = new ArrayBuffer[ExpectedFolding]()
    val textWithoutMarkers = new StringBuilder(fileText.length)
    val openMarkers = mutable.Stack[(Int, String, Boolean)]()

    var pos = 0
    while ({
      val nextStartMarker = fileText.indexOf(FOLD_START_MARKER_BEGIN, pos)
      val nextEndMarker = fileText.indexOf(FOLD_END_MARKER, pos)

      Seq(nextStartMarker -> true, nextEndMarker -> false)
        .filter(_._1 >= 0)
        .sortBy(_._1) match {
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
            expectedRegions += ExpectedFolding(
              TextRange.create(regionBegin, regionEnd),
              replacementText,
              isCollapsedByDefault
            )
          }
          true
        case Seq() =>
          false
      }
    }) ()

    assert(openMarkers.isEmpty, s"Unbalanced fold markers #3: ${openMarkers.mkString}")

    val assumedRegionRanges = expectedRegions.sortBy(_.range.getStartOffset)

    myFixture.configureByText("dummy.scala", textWithoutMarkers.result())

    val myBuilder = new ScalaFoldingBuilder
    val regions = myBuilder.buildFoldRegions(myFixture.getFile.getNode, myFixture getDocument myFixture.getFile)

    assertEquals(s"Different folding regions count", assumedRegionRanges.size, regions.length)

    (regions zip assumedRegionRanges).zipWithIndex foreach {
      case ((region, ExpectedFolding(assumedRange, expectedPlaceholderText, isCollapsedByDefault)), idx) =>
        def differentMessage(what: String) = s"Different $what in region #$idx"

        assertEquals(differentMessage("range"), assumedRange, region.getRange)
        val actualReplacementText = myBuilder.getLanguagePlaceholderText(region.getElement, region.getRange)
        assertEquals(differentMessage("placeholder text"), expectedPlaceholderText, actualReplacementText)

        // using builder instead of `region.isCollapsedByDefault` because latest returns null by default
        // when regions are obtained from `myBuilder.buildFoldRegions`
        val actualIsCollapsedByDefault = myBuilder.isCollapsedByDefault(region)
        assertEquals(differentMessage("'isCollapsedByDefault' value"), isCollapsedByDefault, actualIsCollapsedByDefault)
    }
  }

  private case class ExpectedFolding(range: TextRange, placeholder: String, isCollapsedByDefault: Boolean)

  protected def withModifiedSettings[BeanType](beanInstanceGetter: () => BeanType)(body: BeanType => Unit): RevertableChange =
    new RevertableChange {
      private var settingsBefore: BeanType = _
      private lazy val settings: BeanType = beanInstanceGetter().ensuring(_ != null)

      override def applyChange(): Unit = {
        settingsBefore = XmlSerializerUtil.createCopy(settings)
        body(settings)
      }

      override def revertChange(): Unit =
        XmlSerializerUtil.copyBean(settingsBefore, settings)
    }

  protected def withModifiedScalaFoldingSettings(body: ScalaCodeFoldingSettings => Unit): RevertableChange =
    withModifiedSettings(() => ScalaCodeFoldingSettings.getInstance())(body)

  protected def withModifiedFoldingSettings(body: CodeFoldingSettings => Unit): RevertableChange =
    withModifiedSettings(() => CodeFoldingSettings.getInstance())(body)
}
