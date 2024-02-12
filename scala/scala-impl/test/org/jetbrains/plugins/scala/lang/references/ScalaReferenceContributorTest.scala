package org.jetbrains.plugins.scala.lang.references


import com.intellij.openapi.paths.WebReference
import com.intellij.psi.{PsiFile, PsiReference}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.util.MultilineStringUtil.{MultilineQuotes => quotes}
import org.junit.Assert._

import scala.collection.mutable

class ScalaReferenceContributorTest extends ScalaLightCodeInsightFixtureTestCase {

  def testUrlReference(): Unit = {
    val testUrl = "https://www.site.com/"

    def assertUrlReference(extraMessage: String)(literalText: String): Unit = {
      val text = s"""val value = $literalText"""
      val psiFile = configureFromFileText(text)

      val urlReferenceExpected = testUrl
      val urlReferencesActual  = extractReferences(psiFile).filterByType[WebReference].map(_.getUrl)

      assertEquals(1, urlReferencesActual.size)
      assertEquals(
        s"should detect url reference $extraMessage",
        urlReferenceExpected,
        urlReferencesActual.head
      )
    }

    assertUrlReference("in string literal")(s""""$testUrl"""")
    assertUrlReference("in string literal with leading space")(s"""" $testUrl"""")
    assertUrlReference("in string literal with closing space")(s""""$testUrl """")

    assertUrlReference("in multiline string literal")(s"""$quotes$testUrl$quotes""")
    assertUrlReference("in interpolated string literal")(s"""s"$testUrl"""")
    assertUrlReference("in multiline interpolated string literal")(s"""s$quotes$testUrl$quotes""")
  }

  private def extractReferences(file: PsiFile): Seq[PsiReference] = {
    val found: mutable.Buffer[PsiReference] = mutable.Buffer()

    val visitor = new ScalaRecursiveElementVisitor {
      override def visitLiteral(literal: ScLiteral): Unit = {
        literal match {
          case s: ScStringLiteral if s.isString =>
            found ++= s.getReferences
        }
        super.visitLiteral(literal)
      }
    }
    visitor.visitFile(file)

    found.toSeq
  }

}