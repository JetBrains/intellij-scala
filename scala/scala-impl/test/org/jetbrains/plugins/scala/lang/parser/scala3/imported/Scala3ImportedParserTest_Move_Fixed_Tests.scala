package org.jetbrains.plugins.scala.lang.parser.scala3.imported

import java.nio.file.{Files, Paths, StandardCopyOption}

import com.intellij.openapi.project.Project
import junit.framework.{TestResult, TestSuite}
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Ignore

object Scala3ImportedParserTest_Move_Fixed_Tests {
  val dottyParserTestsSuccessDir: String = TestUtils.getTestDataPath + Scala3ImportedParserTest.directory
  val dottyParserTestsFailDir: String = TestUtils.getTestDataPath +  Scala3ImportedParserTest_Fail.directory

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
    val suite = new Scala3ImportedParserTest_Move_Fixed_Tests()
    suite.run(new TestResult)

    // force stop because test system has still some threads running
    System.exit(0)
  }

  @Ignore("for local running only")
  class Scala3ImportedParserTest_Move_Fixed_Tests
    extends Scala3ImportedParserTestBase(Scala3ImportedParserTest_Fail.directory) {

    protected override def transform(testName: String, fileText: String, project: Project): String = {
      val (errors, _) = findErrorElements(fileText, project)

      if (errors.isEmpty) {
        val from = dottyParserTestsFailDir + "/" + testName + ".test"
        val to = dottyParserTestsSuccessDir + "/" + testName + ".test"

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
