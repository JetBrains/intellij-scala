package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.structuralsearch.MatchOptions
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.{Scala3Language, ScalaFileType}

import scala.annotation.tailrec

class ScalaStructuralSearchTestCase extends StructuralSearchTestCase {

  protected def matchAndAssert(
    name: String,
    @Language("Scala 3") code: String,
    pattern: String,
    modifyOptions: MatchOptions => Unit = _ => ()
  ): Unit = {
    val (plainCode, marker) = extractMarker(code.stripMargin.trim)
    val results = findMatches(plainCode,
      pattern.stripMargin.trim,
      ScalaFileType.INSTANCE,
      Scala3Language.INSTANCE,
      ScalaFileType.INSTANCE,
      false,
      modifyOptions
    )

    assert(results.size == marker.size, s"[StructuralSearch - $name] The number of results does not match")

    for (result <- results) {
      val begin = result.getMatch.getTextRange.getStartOffset
      assert(marker.contains(begin), s"[StructuralSearch - $name] Found match at position $begin where should be no match:\n${result.getMatchImage}")
      val end = marker(begin)
      val expected = plainCode.substring(begin, end)
      assert(end - begin == result.getMatch.getTextRange.getLength, s"[StructuralSearch - $name] Match at position $begin has wrong length\n${result.getMatchImage}\n  instead of\n$expected")
      assert(expected == result.getMatchImage, s"[StructuralSearch - $name] Match at position $begin has wrong content\n${result.getMatchImage}\n  instead of\n$expected")
    }
  }

  private def extractMarker(code: String): (String, Map[Int, Int]) = {
    @tailrec
    def extract(code: String, map: Map[String, (Int, Int)]): (String, Map[Int, Int]) = {
      val begin = code.indexOf("<match=\"")
      val end = code.indexOf("</match=\"")
      if (0 <= begin && begin < end) {
        val ident = code.substring(begin + 8, begin + 10)
        extract(code.replaceFirst(s"<match=\"$ident\">", ""),
          map + (ident -> (begin, -1))
        )
      } else if (0 <= end) {
        val ident = code.substring(end + 9, end + 11)
        extract(code.replaceFirst(s"</match=\"$ident\">", ""),
          map + (ident -> (map.getOrElse(ident, (-1, -1))._1, end))
        )
      } else {
        (code, map.map((_, v) => (v._1, v._2)))
      }
    }

    extract(code, Map())
  }
  
  def clearMarker(code: String, except: Set[String] = Set()): String = {
    @tailrec
    def clearMarker(code: String, except: Set[String] = Set(), fromIndex: Int): String = {
      val begin = code.indexOf("<match=\"", fromIndex)
      if (0 <= begin) {
        val ident = code.substring(begin + 8, begin + 10)
        if (except.contains(ident))
          clearMarker(code, except, begin + 12)
        else
          clearMarker(code.replaceFirst(s"<match=\"$ident\">", "").replaceFirst(s"</match=\"$ident\">", ""), except, begin)
      } else {
        code
      }
    }
    clearMarker(code.stripMargin.trim, except, 0)
  }
}
