package org.jetbrains.plugins.scala.structuralSearch.search

import org.jetbrains.plugins.scala.extensions.{PathExt, StringExt}
import org.jetbrains.plugins.scala.lang.parser.scala3.imported.Scala3ImportedParserTestConfig
import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralSearchTestCase
import org.jetbrains.plugins.scala.util.TestUtils

import java.nio.file.Path
import scala.collection.immutable.ArraySeq

class ScSSMatchCompleteness extends ScalaStructuralSearchTestCase {
  val path = TestUtils.getTestDataPath + "/" + Scala3ImportedParserTestConfig.Newest.successDataDirectory
  private val separatorRegex = raw"\n-{5,}".r
  // files containing some $...$ inside of a string
  private val skips = Set(
    "reference_main-functions.test",
    "i14626.test",
    "t0774_deathname.test",
    "test-typers.test",
    "i16954.test"
  )

  def eliminateBlockComments(oText: String): String = {
    var text = oText
    var counter = 0
    var pos = 0
    var start = 0
    while (text.indexOf("/*", pos) >= 0) {
      val indexStart = text.indexOf("/*", pos)
      val indexEnd = text.indexOf("*/", pos)

      if (indexStart < indexEnd) {
        if (counter == 0)
          start = indexStart

        counter += 1
        pos = indexStart + 2
      } else {
        counter -= 1
        pos = indexEnd + 2

        if (counter == 0) {
          text = text.substring(0, start) + text.substring(indexEnd + 2)
          pos = start
        }
      }
    }
    while (text.indexOf("*/", pos) >= 0) {
      val indexEnd = text.indexOf("*/", pos)
      counter -= 1
      pos = indexEnd + 2

      if (counter == 0) {
        text = text.substring(0, start) + text.substring(indexEnd + 2)
        pos = start
      }
    }
    text
  }

  def testCompleteness(): Unit = {
    val files = Path.of(path)
      .allFiles()
      .to(ArraySeq)
    println(s"Found ${files.size} files. Starting to test them all...")

    var counter = 0
    var success = 0
    var error = 0
    var skipped = 0
    for ((file, i) <- files.zipWithIndex) {
      val text = {
        val text = file.readAllBytesToString().withNormalizedSeparator
        val strippedLineComments = separatorRegex.findFirstMatchIn(text)
          .fold(text)(m => text.substring(0, m.start))
          // get rid of all the comments
          .split("\n").filterNot(_.strip().startsWith("//"))
          .map(line => if line.contains("//") then line.substring(0, line.indexOf("//")) else line)
          .mkString("\n")
        eliminateBlockComments(strippedLineComments)
          .strip()
      }

      try {
        if (text.length < 50000 && !skips.contains(file.getFileName.toString)) {
          matchAndAssert(s"Test all parsing tests. Testcase $i",
            s"""<match="AA">$text</match="AA">""", "",
            _.setSearchPattern(text),
            true, true, false
          )
          success += 1
        } else {
          skipped += 1
          println(s"Skipped file $i - $file")
        }
      } catch {
        case throwable: Throwable =>
          error += 1
          println(s"Failed file $i - $file")
      } finally {
        counter += 1
      }
    }

    println(s"Result: $counter total: $success succeeded - $error errors - $skipped skipped")
    assert(error == 0, s"$error files failed")
    assert(success + skipped == counter, "Not all files succeeded")
  }
}
