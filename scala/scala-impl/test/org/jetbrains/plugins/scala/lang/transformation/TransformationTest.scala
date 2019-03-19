package org.jetbrains.plugins.scala
package lang
package transformation

import com.intellij.openapi.util.{TextRange, text => textUtils}
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.{PsiElement, PsiFile, PsiFileFactory}
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals

/**
  * @author Pavel Fatin
  */
abstract class TransformationTest extends base.ScalaLightCodeInsightFixtureTestAdapter with util.Markers {
  @Language("Scala")
  protected val header: String = ""

  import TransformationTest._
  import extensions._

  protected def transform(element: PsiElement, file: PsiFile, reformat: Transformer.ReformatAction): Unit

  protected def check(@Language("Scala") before: String,
                      @Language("Scala") after: String)
                     (@Language("Scala") header: String = "",
                      @Language("Scala") footer: String = ""): Unit = {
    implicit val headerAndFooter: (String, String) = (createHeader(header), footer)

    val actualFile = configureByText(before)

    // collect all ranges that should be formated
    var actualRewriteTextRanges = List.empty[TextRange]
    val reformat: Transformer.ReformatAction = (textRanges, _, _) => actualRewriteTextRanges :::= textRanges

    actualFile.depthFirst()
      .foreach(transform(_, actualFile, reformat))

    val (afterCode, expectedReformatRanges) = extractMarkers(after)
    val expectedReformatRangesWithHeader = expectedReformatRanges.map(adjustMarkerRanges)
    assertEquals(afterCode.trim, slice(actualFile).trim)

    val expectedFile = configureByText(afterCode)
    assertEquals(psiToString(expectedFile), psiToString(actualFile))

    assertEquals(sortRanges(expectedReformatRangesWithHeader), sortRanges(actualRewriteTextRanges))
  }

  private def createHeader(header: String) =
    s"""$PredefinedHeader
       |${this.header}
       |$header""".stripMargin

  private def configureByText(text: String)
                             (implicit headerAndFooter: (String, String)) = {
    val (header, footer) = headerAndFooter
    val fileText =
      s"""$header
         |$text
         |$footer""".stripMargin

    PsiFileFactory.getInstance(getProject).createFileFromText(
      "foo.scala",
      ScalaFileType.INSTANCE,
      textUtils.StringUtil.convertLineSeparators(fileText)
    )
  }

  private def sortRanges(ranges: Seq[TextRange]) =
    ranges.sorted(Ordering.by((range: TextRange) => (range.getStartOffset, range.getEndOffset))).toList

}

object TransformationTest {
  val ScalaSourceHeader = "import scala.io.Source"

  private val PredefinedHeader =
    s"""class A { def a(): Unit = _ }
       |class B { def b(): Unit = _ }
       |class C { def c(): Unit = _ }
       |object A extends A
       |object B extends B
       |object C extends C""".stripMargin

  private def psiToString(file: PsiFile): String =
    DebugUtil.psiToString(file, false)

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
