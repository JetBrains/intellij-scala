package org.jetbrains.plugins.scala.lang.parser

import java.nio.file.{Files, Paths, StandardCopyOption}

import com.intellij.openapi.project.Project
import junit.framework.{TestResult, TestSuite}

object Scala3ImportedParserTest_Move_Fixed_Tests {
  /**
   * Run this main method to move all scala 3 test files that generate no PsiErrorElements anymore to
   * the succeeding directory
   *
   * Use this if you have made progress in the parser and fixed files that produced PsiErrorElement
   * and, now, make Scala3ImportedParserTest_Fail fail. In this case this method will move those
   * into the succeeding folder, so they can fail if someone screws anything up in the parser, that
   * had previously worked.
   */
  def main(args: Array[String]): Unit = {
    val suite = createMovingScala3ImportedParserTest()
    suite.run(new TestResult)

    // force stop because test system has still some threads running
    System.exit(0)
  }

  private def createMovingScala3ImportedParserTest(): TestSuite = {
    val succDir = Scala3ImportedParserTest.directory
    val failDir = Scala3ImportedParserTest_Fail.directory

    new Scala3ImportedParserTestBase(failDir) {
      protected override def transform(testName: String, fileText: String, project: Project): String = {
        val (errors, _) = findErrorElements(fileText, project)

        if (errors.isEmpty) {
          val from = getTestDataPath + failDir + "/" + testName + ".test"
          val to = getTestDataPath + succDir + "/" + testName + ".test"

          println("Move " + from)
          println("  to " + to)
          Files.move(
            Paths.get(from),
            Paths.get(to),
            StandardCopyOption.REPLACE_EXISTING
          )
        }

        // all files of failing test have no ast to test against, so return an empty string here
        ""
      }

      override protected def shouldHaveErrorElements: Boolean = throw new UnsupportedOperationException
    }
  }
}

