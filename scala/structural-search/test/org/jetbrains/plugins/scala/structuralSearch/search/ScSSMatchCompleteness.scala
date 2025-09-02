package org.jetbrains.plugins.scala.structuralSearch.search

import org.jetbrains.plugins.scala.extensions.{PathExt, StringExt}
import org.jetbrains.plugins.scala.lang.parser.scala3.imported.Scala3ImportedParserTestConfig
import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralSearchTestCase
import org.jetbrains.plugins.scala.util.TestUtils

import java.nio.file.Path
import scala.collection.immutable.ArraySeq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future, duration}

class ScSSMatchCompleteness extends ScalaStructuralSearchTestCase {
  val path = TestUtils.getTestDataPath + "/" + Scala3ImportedParserTestConfig.Newest.successDataDirectory
  private val separatorRegex = raw"\n-{5,}".r

  def testCompleteness(): Unit = {
    val files = Path.of(path)
      .allFiles()
      .to(ArraySeq)
    print(s"Found ${files.size} files. Starting to test them all...")

    var counter = 0
    var success = 0
    var error = 0
    var skipped = 0
    for ((file, i) <- files.zipWithIndex) {
      val text = {
        val text = file.readAllBytesToString().withNormalizedSeparator
        separatorRegex.findFirstMatchIn(text)
          .fold(text)(m => text.substring(0, m.start))
      }

      try {
        if (text.length < 50000) {
          matchAndAssert(s"Test all parsing tests. Testcase $i",
            text, s"""<match="AA">$text</match="AA">"""
          )
          success += 1
        } else {
          skipped += 1
          println(s"Skipped file $i due to large size - $file")
        }
      } catch {
        case _: Throwable =>
          error += 1
          println(s"Failed file $i - $file")
      } finally {
        counter += 1
      }
    }

    println(s"Result: $counter total: $success succeeded - $error errors - $skipped skipped")
    assert(error == 0, s"$error cases failed")
    assert(success == counter, "Not all cases succeeded")
  }
}
