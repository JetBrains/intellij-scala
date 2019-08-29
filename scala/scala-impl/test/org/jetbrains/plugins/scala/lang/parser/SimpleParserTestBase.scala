package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.psi.PsiErrorElement
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.ScalaFeatureSettings

trait SimpleParserTestBase extends SimpleTestCase {
  def err(err: String): String = {
    s"[[Err($err)]]"
  }
  private val errRegex = raw"\[\[Err\((.+)\)\]\]".r

  private def extractExpectedErrors(text: String): (String, Set[(Int, String)]) = {
    var code = text
    val errorsBuilder = Set.newBuilder[(Int, String)]
    while(true) {
      errRegex.findFirstMatchIn(code) match {
        case Some(matc) =>
          code = code.substring(0, matc.start) + code.substring(matc.end)
          errorsBuilder += (matc.start -> matc.group(1))
        case None =>
          return code -> errorsBuilder.result()
      }
    }
    ???
  }

  def checkParseErrors(text: String): ScalaFile = {
    val (code, expectedErrors) = extractExpectedErrors(
      s"""
         |object O {
         |  $text
         |}
         |""".stripMargin
    )


    val file = withActiveFeatureSettings{
      parseText(code)
    }
    val errors = file.depthFirst().toSeq.filterBy[PsiErrorElement]
    if (expectedErrors.isEmpty) {
      assert(errors.isEmpty, "Expected no errors but found: " + errors.map(_.getErrorDescription).mkString(", "))
    } else {
      val errorsWithPos = errors.map(psi => psi.getTextOffset -> psi.getErrorDescription).toSet
      val notFoundErrors = (expectedErrors -- errorsWithPos).toSeq.sortBy(_._1).map(_._2)
      val unexpectedErrors = (errorsWithPos -- expectedErrors).toSeq.sortBy(_._1).map(_._2)
      assert(unexpectedErrors.isEmpty, "Found unexpected errors: " + unexpectedErrors.mkString(", "))
      assert(notFoundErrors.isEmpty, "Expected errors but didn't find: " + notFoundErrors.mkString(", "))
    }
    file
  }

  private def withActiveFeatureSettings[T](body: => T): T = {
    val settings = ScalaFeatureSettings.instanceIn(fixture.getProject)
    val old = settings.enabled
    settings.enabled = true
    try body
    finally settings.enabled = old
  }
}
