package org.jetbrains.plugins.scala.structuralSearch.search

import com.intellij.structuralsearch.MalformedPatternException
import org.jetbrains.plugins.scala.Scala3Language
import org.jetbrains.plugins.scala.extensions.executionContext.appExecutionContext
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PathExt, StringExt, inReadAction}
import org.jetbrains.plugins.scala.lang.parser.scala3.imported.Scala3ImportedParserTestConfig
import org.jetbrains.plugins.scala.structuralSearch.{Scala3FileType, ScalaStructuralSearchTestCase}
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.assertions.ExceptionAssertions.assertException

import java.nio.file.Path
import scala.collection.immutable.ArraySeq
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

/**
 * Turns every file of the Scala 3 imported parser test data into a structural search pattern and asserts that the
 * pattern matches the very file it was built from.
 *
 * The files are checked in parallel batches. Note that this is not JUnit's parallel execution - the whole thing is
 * a single test method that fans out internally.
 *
 * Every worker compiles its patterns against its own [[com.intellij.structuralsearch.MatchOptions]] and returns its
 * own outcomes, so the parallel phase shares no mutable state and needs no synchronization. The outcomes are printed
 * and counted after the join, in the original file order, which keeps the log identical to the sequential version.
 */
class ScSSMatchCompleteness extends ScalaStructuralSearchTestCase {
  val path = TestUtils.getTestDataPath + "/" + Scala3ImportedParserTestConfig.Newest.successDataDirectory
  private val separatorRegex = raw"\n-{5,}".r

  /** Files at least this long are skipped: compiling a search pattern that big is hopeless. */
  private val maxPatternLength = 50000

  /** The number of batches the files are split into, each checked by its own future. */
  protected def numBatches: Int = Runtime.getRuntime.availableProcessors

  /**
   * The test body fans out onto the application pool and waits for the result. Awaiting that while holding the EDT
   * would starve every event the matching path might need.
   */
  override protected def runInDispatchThread(): Boolean = false

  /**
   * [[com.intellij.testFramework.LightPlatformCodeInsightTestCase]] runs the test inside a command, and
   * `CommandProcessor` asserts that it is called on the EDT. This test never touches an editor, so it opts out
   * instead.
   */
  override protected def isRunInCommand: Boolean = false

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

    warmUpMatcher()

    // Deal the files round-robin rather than in contiguous chunks: allFiles() returns them grouped by directory, so
    // contiguous chunks would pile one directory - and whatever large files it happens to hold - into a single batch
    // and leave it as the straggler.
    val batches = files.zipWithIndex.groupMap(_._2 % numBatches)(identity).values.toSeq
    val futures = batches.map { batch =>
      Future {
        batch.map((file, index) => index -> checkFile(index, file))
      }
    }
    val outcomes = Await.result(Future.sequence(futures), 30.minutes).flatten.sortBy(_._1).map(_._2)

    // Reported here rather than from the batches, in the original file order, so that the log matches the sequential
    // version and neither the printing nor the counting needs to be synchronized.
    outcomes.foreach {
      case FileOutcome.Succeeded => ()
      case FileOutcome.Skipped(message) => println(message)
      case FileOutcome.Failed(message) => println(message)
    }

    val counter = outcomes.size
    val success = outcomes.count(_ == FileOutcome.Succeeded)
    val skipped = outcomes.count(_.is[FileOutcome.Skipped])
    val error = outcomes.count(_.is[FileOutcome.Failed])

    println(s"Result: $counter total: $success succeeded - $error errors - $skipped skipped")
    assert(error == 0, s"$error files failed")
    assert(success + skipped == counter, "Not all files succeeded")
  }

  /**
   * Runs the whole per-file pipeline and reports the result instead of updating shared counters, so that the batches
   * can run concurrently without any synchronization.
   */
  private def checkFile(index: Int, file: Path): FileOutcome = {
    val text = patternTextOf(file)

    try {
      // The pattern and the source are parsed into PSI and traversed here. On the EDT the sequential predecessor had
      // read access implicitly; on the worker threads it must be explicit. Per file rather than per batch, so that no
      // thread holds a read action for the whole run.
      inReadAction {
        if (text.isEmpty) {
          // Empty files cannot form a search pattern.
          assertException[MalformedPatternException] {
            matchAndAssert(newMatchOptions(), s"Test all parsing tests. Testcase $index", "", "", _ => (), true, true, true)
          }
          FileOutcome.Succeeded
        } else if (text.length < maxPatternLength) {
          matchAndAssert(newMatchOptions(), s"Test all parsing tests. Testcase $index",
            wholeFileMatch(text), "",
            // Search patterns use live-template syntax: escape literal dollar signs as $$.
            _.setSearchPattern(text.replace("$", "$$")),
            true, true, false
          )
          FileOutcome.Succeeded
        } else {
          FileOutcome.Skipped(s"Skipped file $index - $file")
        }
      }
    } catch {
      case throwable: Throwable =>
        FileOutcome.Failed(s"Failed file $index - $file: $throwable")
    }
  }

  /**
   * Marks the whole source as the single expected match.
   *
   * The markers are concatenated rather than written out as one literal: `code` carries a Scala language injection,
   * and a literal `<match="AA">...</match="AA">` inside it is parsed as a malformed XML literal.
   */
  private def wholeFileMatch(text: String): String = {
    val marker = "AA"
    s"""<match="$marker">""" + text + s"""</match="$marker">"""
  }

  /** Drops the expected-output section and all comments, leaving the source text the search pattern is built from. */
  private def patternTextOf(file: Path): String = {
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

  /**
   * Compiles and runs one throwaway pattern before the fan-out. Several platform caches on the matching path are
   * plain unsynchronized statics filled on first use - most notably the profile-by-language `HashMap` of
   * [[com.intellij.structuralsearch.StructuralSearchUtil]] - so they are populated on this thread while nothing else
   * is reading them. The matches themselves are irrelevant, only the fact that the whole path has been walked once.
   */
  private def warmUpMatcher(): Unit = inReadAction {
    val warmUpCode = "val x = 1"
    findMatches(newMatchOptions(), warmUpCode, "", Scala3FileType, Scala3Language.INSTANCE, Scala3FileType, false,
      _.setSearchPattern(warmUpCode)
    )
  }

  /** The result of checking a single file, owned by the worker that produced it. */
  private enum FileOutcome:
    case Succeeded
    case Skipped(message: String)
    case Failed(message: String)
}
