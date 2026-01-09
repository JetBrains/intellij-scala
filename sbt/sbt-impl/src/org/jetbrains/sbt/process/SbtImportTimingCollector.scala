package org.jetbrains.sbt.process

import com.intellij.openapi.diagnostic.Logger

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

/**
 * Collects and aggregates timing information from project imports, including sbt task timings from the `sbt-structure` plugin
 * and import operations within the Scala plugin.
 *
 * This collector is enabled via the registry key `sbt.import.time.measurement` and doesn't work when import in the sbt shell is enabled.
 * When used, the JVM option `-Dsbt.task.timings=true` is passed to the sbt process during import, which causes
 * sbt to output detailed timing information for each task. This information is parsed and collected by this class.
 *
 * After import completes, two files are generated:
 *   - `.idea/sbt-import-history.txt` - raw timing data from each import
 *   - `.idea/sbt-import-summary.txt` - aggregated statistics across all imports
 */
private[sbt] object SbtImportTimingCollector:
  private val Log = Logger.getInstance(getClass)

  private case class ImportStep(name: String, timeMs: Long)

  /**
   * A file that contains the timing results from each import in a separate line, formatted as:
   * {{{
   * timestamp|stepName1:time1,time2;stepName2:time3,time4
   * }}}
   * Multiple times for the same step indicate multiple executions within a single import.
   */
  val historyFile = ".idea/sbt-import-history.txt"
  /** A file that contains aggregated results from all imports. It's automatically updated after each import. */
  val summaryFile = ".idea/sbt-import-summary.txt"

  /** A collector that accumulates timing data during import. */
  class TimingCollector(projectDirectory: Path):
    private val steps = mutable.ArrayBuffer[ImportStep]()

    /** It's necessary to measure the times only after the server has started to ensure the timings are collected only from the import command. */
    private val ServerStartedMarker = "[info] started sbt server"
    private var sbtServerStarted = false

    private val historyFilePath = projectDirectory.resolve(historyFile)

    private val summaryFilePath = projectDirectory.resolve(summaryFile)

    /** Process a line of sbt output to extract timing information. */
    def processSbtOutputLine(line: String): Unit = {
      val text = line.trim

      if (sbtServerStarted)
        parseSbtTaskTiming(text).foreach(steps.addOne)

      if (text.contains(ServerStartedMarker))
        sbtServerStarted = true
    }

    def addScalaPluginTimings(operations: (String, Long)*): Unit = {
      val pluginOperations = operations.map { case (name, time) => ImportStep(s"(Scala plugin) $name", time) }
      steps.addAll(pluginOperations)
    }

    /** Writes the steps from the current import to the history file and updates the summary. */
    def writeTimingResults(): Unit = {
      saveCurrentTimings()
      regenerateSummary()
    }

    /** Appends the timing results from the most recently completed import to the history file. */
    private def saveCurrentTimings(): Unit =
      try {
        val currentImportSteps = steps.toSeq.groupMap(_.name)(_.timeMs)
        val data = currentImportSteps.map { case (name, times) =>
          s"$name:${times.mkString(",")}"
        }.mkString(";")

        val timeStamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss").format(LocalDateTime.now())
        val line = s"$timeStamp|$data\n"
        Files.writeString(historyFilePath, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
      } catch {
        case ex: Exception =>
          Log.warn(s"[SbtImportTimingCollector] Failed to append import steps from the most recent import to $historyFilePath", ex)
      }

    /**
     * Regenerates the summary file by reading all timing data from [[historyFile]], aggregating statistics
     * across all recorded imports, and writing a formatted summary to [[summaryFile]].
     */
    private def regenerateSummary(): Unit =
      try {
        val allImports = readTimingHistory(historyFilePath)
        if (allImports.isEmpty) return

        // Aggregate statistics across all imports
        val allSteps = allImports.flatMap(_.data).groupMapReduce(_._1)(_._2)(_ ++ _)

        // Compute aggregate statistics
        val aggregateStats = allSteps.map { case (name, times) =>
          val count = times.size
          val min = times.min
          val max = times.max
          val avg = times.sum.toDouble / count
          (name, count, avg, min, max)
        }.toSeq.sortBy(_._3).reverse

        // Generate summary content
        val statsLines = aggregateStats.map { case (name, count, avg, min, max) =>
          f"$name%-60s $count%8d $avg%10.1f $min%10d $max%10d"
        }.mkString("\n")

        val summary =
          s"""Import Timing Summary (aggregated across all imports)
             |Total imports: ${allImports.size}
             |${"=" * 100}
             |${f"${"Operation"}%-60s ${"Count"}%8s ${"Avg (ms)"}%10s ${"Min (ms)"}%10s ${"Max (ms)"}%10s"}
             |${"-" * 100}
             |$statsLines
             |""".stripMargin

        Files.writeString(summaryFilePath, summary, StandardCharsets.UTF_8)
      } catch {
        case ex: Exception =>
          Log.warn("[SbtImportTimingCollector] Failed to regenerate the summary file", ex)
      }

  end TimingCollector

  /**
   * Represents the timing data for a single sbt import.
   *
   * @param data mapping of step names to a sequence of their durations
   *             (steps, especially sbt tasks, can be executed multiple times during a single import).
   */
  private case class ImportTimingData(timestamp: String, data: Map[String, Seq[Long]])

  /**
   * Parses a timing line from sbt output. Extracts the task name and time.
   *
   * @param line the task timing info like:
   *   - [info] foo / updateClassifiers / classifiersModule: 2 ms
   *   - [info] root / taskData                            : 3 ms
   *   - [info] state                                      : 7 ms
   */
  private def parseSbtTaskTiming(line: String): Option[ImportStep] = {
    val pattern = """^\[info]\s+(.+?)\s*:\s*(\d+)\s*ms$""".r

    line.trim match {
      case pattern(fullTaskPath, timeStr) =>
        // Extract task name: take the part after the last '/', or the whole path if no '/'
        val taskName = fullTaskPath.split('/').last.trim
        Some(ImportStep(taskName, timeStr.toLong))
      case _ => None
    }
  }

  /**
   * Parses a single step entry from the format "stepName:time1,time2,time3"
   *
   * @return `None` if parsing fails or if no valid times are found.
   */
  private def parseStepEntry(entry: String): Option[(String, Seq[Long])] =
    entry.split(':') match {
      case Array(name, timesStr) =>
        val times = timesStr.split(',').flatMap(_.toLongOption).toSeq
        Option.when(times.nonEmpty)(name -> times)
      case _ => None
    }

  /**
   * Parses a raw line from the history file into an [[ImportTimingData]].
   *
   * @see [[TimingCollector.historyFile]] for the expected line format.
   */
  private def parseHistoryLine(line: String): Option[ImportTimingData] =
    line.split('|') match {
      case Array(timestamp, stepsStr) =>
        val steps = stepsStr
          .split(';')
          .flatMap(parseStepEntry)
          .toMap

        Option.when(steps.nonEmpty)(ImportTimingData(timestamp, steps))
      case _ => None
    }

  /**
   * Reads all imports timing data from the history file.
   *
   * @see [[org.jetbrains.sbt.process.SbtImportTimingCollector.TimingCollector.historyFile]]
   */
  private def readTimingHistory(historyFile: Path): Seq[ImportTimingData] =
    try {
      Files
        .readAllLines(historyFile, StandardCharsets.UTF_8)
        .asScala
        .flatMap(parseHistoryLine)
        .toSeq
    } catch {
      case ex: Exception =>
        Log.warn(s"[SbtImportTimingCollector] Failed to read timing history from $historyFile", ex)
        Nil
    }
