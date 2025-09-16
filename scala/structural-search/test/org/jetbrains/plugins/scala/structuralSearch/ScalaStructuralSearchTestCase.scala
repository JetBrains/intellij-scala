package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.dupLocator.iterators.SiblingNodeIterator
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.structuralsearch.impl.matcher.CompiledPattern
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler
import com.intellij.structuralsearch.{MatchOptions, MatchResult, Matcher}
import org.intellij.lang
import org.jetbrains.plugins.scala.{Scala3Language, ScalaFileType, ScalaLanguage}

import scala.annotation.tailrec
import scala.jdk.CollectionConverters.CollectionHasAsScala

abstract class ScalaStructuralSearchTestCase extends StructuralSRTestCase {

  protected def matchAndAssert(
    name: String,
    @lang.annotations.Language("Scala 3") code: String,
    @lang.annotations.Language("Scala 3") pattern: String,
    modifyOptions: MatchOptions => Unit = _ => (),
    inScala3: Boolean = true,
    patternScala3: Boolean = true,
    stripMargin: Boolean = true
  ): Unit = {
    val (plainCode, marker) = extractMarker((if stripMargin then code.stripMargin else code).trim)
    val results = findMatches(plainCode,
      (if stripMargin then pattern.stripMargin else pattern).trim,
      if inScala3 then Scala3FileType else ScalaFileType.INSTANCE,
      if inScala3 then Scala3Language.INSTANCE else ScalaLanguage.INSTANCE,
      if patternScala3 then Scala3FileType else ScalaFileType.INSTANCE,
      false,
      modifyOptions
    )

    assert(results.size == marker.size, s"[StructuralSearch - $name] The number of results does not match (${results.size} instead of ${marker.size})")

    // need to deal with multiple children
    for (result <- results) {
      val multi = MatchResult.MULTI_LINE_MATCH.equals(result.getName)
      val children = result.getChildren.stream().filter(r => MatchResult.LINE_MATCH.equals(r.getName)).toList
      val begin = (if (multi) children.get(0) else result).getMatch.getTextRange.getStartOffset

      val (length, text) = if (multi) {
        assert(!children.isEmpty)
        val sb = new StringBuilder()
        val it = SiblingNodeIterator.create(children.get(0).getMatch)
        val last = children.get(children.size() - 1).getMatch
        var length = 0
        while (it.current() != null && it.current() != last) {
          sb.append(it.current().getText)
          length += it.current().getTextRange.getLength
          it.advance()
        }
        sb.append(last.getText)
        length += last.getTextLength
        (length, sb.mkString)
      } else {
        (result.getMatch.getTextRange.getLength, result.getMatchImage)
      }

      assert(marker.contains(begin), s"[StructuralSearch - $name] Found match at position $begin where should be no match:\n${result.getMatchImage}")
      val exEnd = marker(begin)
      val expected = plainCode.substring(begin, exEnd)
      assert(exEnd - begin == length, s"[StructuralSearch - $name] Match at position $begin has wrong length\n${result.getMatchImage}\n  instead of\n$expected")
      assert(expected == text, s"[StructuralSearch - $name] Match at position $begin has wrong content\n${result.getMatchImage}\n  instead of\n$expected")
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

  protected def findMatches(in: String,
                            pattern: String,
                            patternFileType: LanguageFileType,
                            patternLanguage: Language,
                            sourceFileType: LanguageFileType,
                            physicalSourceFile: Boolean,
                            modifyOptions: MatchOptions => Unit,
                           ): Seq[MatchResult] = {
    options.fillSearchCriteria(pattern)
    options.setFileType(patternFileType)
    options.setDialect(patternLanguage)
    modifyOptions(options)
    val compiledPattern: CompiledPattern = PatternCompiler.compilePattern(getProject, options, true, false)
    val message: String = StructuralSRTestCase.checkApplicableConstraints(options, compiledPattern)
    assert(message == null)
    val matcher: Matcher = new Matcher(getProject, options, compiledPattern)
    matcher.testFindMatches(in, true, sourceFileType, physicalSourceFile).asScala.toSeq
  }
}
