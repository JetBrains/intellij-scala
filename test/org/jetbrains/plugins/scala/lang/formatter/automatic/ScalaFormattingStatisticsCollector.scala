package org.jetbrains.plugins.scala
package lang.formatter.automatic

import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.lang.ASTNode
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.formatting.Indent
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase
import org.jetbrains.plugins.scala.util.TestUtils
import java.io._
import scala.collection.mutable
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.statistics.FormattingStatistics
import junit.framework.Test
import scala.Some
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.matching.{ScalaBlockFormatterEntry, ScalaFormattingRuleInstance, ScalaFormattingRuleMatcher}
import org.jdom.Element
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.serialization.ScalaFormattingSettingsSerializer
import com.intellij.openapi.components.StoragePathMacros
import org.jetbrains.jps.model.serialization.PathMacroUtil

/**
 * This is just a dirty hack used to get to Project object.
 * @param path
 */
class ScalaFormattingStatisticsCollector private (path: String) extends BaseScalaFileSetTestCase(path) {

  private val statistics = mutable.Map[Path, mutable.Map[Path, FormattingStatistics]]()

  private var rootNameCount = 0


  def printStatistics(directory: Path) = {

    val log = new File("projectFormattingStatistics")

    val writer = new BufferedWriter(new FileWriter(log))

    val totalLog = new File("formattingStatistics")

    val globalWriter = new BufferedWriter(new FileWriter(totalLog))

    val totalRuleStatisticsMap = mutable.Map[ScalaFormattingRuleInstance, mutable.Map[ScalaBlockFormatterEntry, Int]]()

    for ((statProject, projectStatistics) <- statistics) {

      writer.write("********** Project " + statProject + " **********\n")

      val projectRuleStatisticsMap = mutable.Map[ScalaFormattingRuleInstance, mutable.Map[ScalaBlockFormatterEntry, Int]]()
      //first, log statistics for every single file
      for ((path, statistics) <- projectStatistics) {
//        writer.write("---------- Source file " + path + " ----------\n")

        //now write data on all rules present in the statistics
        val settings = statistics.getInitialSettings
        for ((ruleInstance, entries) <- settings) {
            val instanceMap = projectRuleStatisticsMap.getOrElse(ruleInstance, mutable.Map())
            entries.map(entry => instanceMap.put(entry, entry.instances.size + instanceMap.getOrElse(entry, 0)))
            projectRuleStatisticsMap.put(ruleInstance, instanceMap)

            val globalInstanceMap = totalRuleStatisticsMap.getOrElse(ruleInstance, mutable.Map())
            entries.map(entry => globalInstanceMap.put(entry, entry.instances.size + globalInstanceMap.getOrElse(entry, 0)))
            totalRuleStatisticsMap.put(ruleInstance, globalInstanceMap)
        }
      }

      val projectRuleStatistics = mutable.Map[ScalaFormattingRuleInstance, List[(ScalaBlockFormatterEntry, Int)]]()

      for ((ruleInstance, entriesMap) <- projectRuleStatisticsMap) {
        projectRuleStatistics.put(ruleInstance, entriesMap.toList.sortBy(arg => -arg._2))
      }

//      writer.write("%%%%%%%%%% Project " + statProject + " rule statistics %%%%%%%%%%\n")

      for ((ruleInstance, entries) <- projectRuleStatistics) {
        val instanceString = ruleInstance.getTreeString
        writer.write("\n========== RULE ==========:\n" + instanceString)
        writer.write("\n++++++++++ ENTRIES ++++++++++:\n")
        for ((entry, blocks) <- entries) {
          writer.write(entry.toString + " occured in " + blocks + " blocks\n")
        }
      }
    }

    val ruleStatistics = mutable.Map[ScalaFormattingRuleInstance, List[(ScalaBlockFormatterEntry, Int)]]()

    for ((ruleInstance, entriesMap) <- totalRuleStatisticsMap) {
      ruleStatistics.put(ruleInstance, entriesMap.toList.sortBy(arg => -arg._2))
    }

    for ((ruleInstance, entries) <- ruleStatistics) {
      val instanceString = ruleInstance.getTreeString
      globalWriter.write("\n========== RULE ==========:\n" + instanceString)
      globalWriter.write("\n++++++++++ ENTRIES ++++++++++:\n")
      if (entries.nonEmpty) {
        val (headEntry, headBlocks) = entries.head
        for ((entry, blocks) <- entries.filter(arg => headBlocks / arg._2 < 10)) {
          globalWriter.write(entry.toString + " occured in " + blocks + " blocks\n")
        }
      }
    }

    globalWriter.flush()
    globalWriter.close()
    writer.flush()
    writer.close()
  }

  def getRoot(code: String): ScalaBlock = {
    val project: Project = getProject
    val containingFile: PsiFile = TestUtils.createPseudoPhysicalScalaFile(project, code)
    assert(containingFile.getFileType eq ScalaFileType.SCALA_FILE_TYPE)
    val astNode: ASTNode = containingFile.getNode
    assert(astNode != null)
    val codeStyleSettings: CodeStyleSettings = new CodeStyleSettings
    new ScalaBlock(null, astNode, null, null, Indent.getAbsoluteNoneIndent, null, codeStyleSettings)
  }

  def this() {
    this(
      if (System.getProperty("path") != null)
        System.getProperty("path")
      else
        new File(TestUtils.getTestDataPath + ScalaFormattingStatisticsCollector.DATA_PATH).getCanonicalPath)
  }

  def transform(testName: String, data: Array[String]): String = {
    //assume that test data contains names of project directories
    val path = Paths.get(data(0))
    rootNameCount = path.getNameCount
    val projectDirectories = data(1).split("\n")
    val project = getProject
    for (statProject <- projectDirectories.map(path.resolve)) {
      println(statProject)
      val localStatistics = mutable.Map[Path, FormattingStatistics]()
      Files.walkFileTree(statProject,
        new SimpleFileVisitor[Path]() {
          override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
            if (file.toFile.getName.endsWith(".scala")) {
              val code = new String(FileUtil.loadFileText(file.toFile, "UTF-8"))
              val codeRoot = getRoot(code)
              val matcher = ScalaFormattingRuleMatcher.createDefaultMatcher(project)
//              localStatistics.put(file, collectStatistics(file, matcher, codeRoot))
            }
            FileVisitResult.CONTINUE
          }
        }
      )
      statistics.put(statProject, localStatistics)
    }
    val dumpPath = Paths.get("C:\\formattingStatistics")
    printStatistics(dumpPath)

    //collect per-project statistics

    "OK"
  }
}

object ScalaFormattingStatisticsCollector {

  private val DATA_PATH: String = "/formatter/automatic/data/statistics"

  def suite: Test = new ScalaFormattingStatisticsCollector()
}
