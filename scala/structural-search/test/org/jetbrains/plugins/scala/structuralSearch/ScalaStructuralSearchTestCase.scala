package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.structuralsearch.MatchResult
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaFileType

class ScalaStructuralSearchTestCase extends StructuralSearchTestCase {

  private def findMatches(@Language("Scala 3") in: String, pattern: String): Seq[MatchResult] = {
    super.findMatches(in, pattern, ScalaFileType.INSTANCE)
  }

  private def findMatchesCount(@Language("Scala 3") in: String, pattern: String): Int =
    super.findMatchesCount(in, pattern, ScalaFileType.INSTANCE)

  protected def findAndMatch(
    name: String,
    @Language("Scala 3") in: String,
    pattern: String,
    expected: Seq[String]): Unit = {
    val results = findMatches(in.stripMargin.trim, pattern.stripMargin.trim)
    assert(results.size == expected.size, s"[StructuralSearch - $name] The number of results does not match")

    for ((result, exp) <- results.zip(expected)) {
      assert(result.getMatchImage == exp.stripMargin.trim, s"[StructuralSearch - $name] Could not find result \n$exp instead found \n${result.getMatchImage}")
    }
  }
}
