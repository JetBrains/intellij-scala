package org.jetbrains.plugins.scala
package lang
package transformation

import com.intellij.openapi.util.TextRange
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.{PsiElement, PsiFile, PsiFileFactory}
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.extensions._
import org.junit.Assert.assertEquals

abstract class TransformationTest extends base.ScalaLightCodeInsightFixtureTestAdapter with util.Markers {
  @Language("Scala")
  protected val header: String = ""

  import TransformationTest._

  protected def transform(element: PsiElement, file: PsiFile, reformat: Transformer.ReformatAction): Unit

  protected final def check(@Language("Scala") before: String,
                            @Language("Scala") after: String)
                           (@Language("Scala") header: String = "",
                            @Language("Scala") footer: String = ""): Unit = {
    doCheck(
      before.withNormalizedSeparator,
      after.withNormalizedSeparator
    )(
      header.withNormalizedSeparator,
      footer.withNormalizedSeparator
    )
  }

  private def doCheck(@Language("Scala") before: String,
                      @Language("Scala") after: String)
                     (@Language("Scala") header: String,
                      @Language("Scala") footer: String): Unit = {
    implicit val headerAndFooter: (String, String) = (createHeader(header), footer)

    val actualFile = configureByText(before)

    // collect all ranges that should be formatted
    var actualRewriteTextRanges = List.empty[TextRange]
    val reformat: Transformer.ReformatAction = (textRanges, _, _) => actualRewriteTextRanges :::= textRanges

    actualFile.depthFirst()
      .foreach(transform(_, actualFile, reformat))

    val (afterCode, expectedReformatRanges) = extractNumberedMarkers(after)
    val expectedReformatRangesWithHeader = expectedReformatRanges.map(adjustMarkerRanges)
    assertEquals(afterCode.trim, slice(actualFile).trim)

    val expectedFile = configureByText(afterCode)
    assertEquals(psiToString(expectedFile), psiToString(actualFile))

    assertEquals(
      sortRanges(expectedReformatRangesWithHeader),
      sortRanges(actualRewriteTextRanges)
    )
  }

  private def createHeader(header: String) =
    s"""$PredefinedHeader
       |${this.header}
       |$header""".stripMargin.withNormalizedSeparator

  private def configureByText(text: String)
                             (implicit headerAndFooter: (String, String)): PsiFile = {
    val (header, footer) = headerAndFooter
    val fileText =
      s"""$header
         |$text
         |$footer""".stripMargin.withNormalizedSeparator

    PsiFileFactory.getInstance(getProject).createFileFromText(
      "foo.scala",
      ScalaFileType.INSTANCE,
      fileText
    )
  }

  private def sortRanges(ranges: Seq[TextRange]) =
    ranges.sorted(Ordering.by((range: TextRange) => (range.getStartOffset, range.getEndOffset))).toList

}

object TransformationTest {
  val ScalaSourceHeader = "import scala.io.Source"

  private val PredefinedHeader: String =
    s"""class A { def a(): Unit = _ }
       |class B { def b(): Unit = _ }
       |class C { def c(): Unit = _ }
       |object A extends A
       |object B extends B
       |object C extends C""".stripMargin.withNormalizedSeparator

  private def psiToString(file: PsiFile): String =
    DebugUtil.psiToString(file, true)

  private def slice(file: PsiFile)
                   (implicit headerAndFooter: (String, String)): String = {
    val (header, footer) = headerAndFooter
    val text = file.getText
    text.substring(header.length + 1, text.length - (footer.length + 1))
  }

  private def adjustMarkerRanges(range: TextRange)
                          (implicit headerAndFooter: (String, String)): TextRange = {
    val (header, _) = headerAndFooter
    range.shiftRight(header.length + 1)
  }
}

abstract class TransformerTest(private val transformer: Transformer) extends TransformationTest {
  override protected final def transform(element: PsiElement, file: PsiFile, reformat: Transformer.ReformatAction): Unit =
    Transformer.applyTransformerAndReformat(element, file, transformer, reformat)
}
