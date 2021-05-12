package org.jetbrains.plugins.scala.lang.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.impl.DebugUtil.psiToString
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt, PsiNamedElementExt, StringExt}
import org.jetbrains.plugins.scala.lang.parser.ScalaParserTestOps.extractExpectedErrors
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.junit.Assert.assertEquals

trait ScalaParserTestOps {

  def parseText(text: String): ScalaFile

  def checkTree(text: String, expectedTree: String): Unit = {
    val file = parseText(text.withNormalizedSeparator)
    val resultTree = psiToString(file, false).replace(": " + file.name, "")
    assertEquals(expectedTree.trim.withNormalizedSeparator, resultTree.trim)
  }

  def err(err: String): String =
    s"[[Err($err)]]"

  def checkParseErrors(text: String): ScalaFile = {
    val (code, expectedErrors) = extractExpectedErrors(text)

    val file = parseText(code)
    val errors = file.depthFirst().toSeq.filterByType[PsiErrorElement]
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
}

object ScalaParserTestOps {

  private val errRegex = raw"\[\[Err\((.+)\)\]\]".r

  def extractExpectedErrors(text: String): (String, Set[(Int, String)]) = {
    var code = text
    val errorsBuilder = Set.newBuilder[(Int, String)]
    while (true) {
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
}
