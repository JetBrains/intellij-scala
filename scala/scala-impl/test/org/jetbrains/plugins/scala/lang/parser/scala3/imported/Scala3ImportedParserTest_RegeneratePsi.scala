package org.jetbrains.plugins.scala.lang.parser.scala3.imported

import com.intellij.openapi.project.Project
import com.intellij.psi.impl.DebugUtil.psiToString
import junit.framework.{Test, TestCase}
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Ignore

import java.nio.file.{Files, Paths, StandardCopyOption, StandardOpenOption}

@Ignore("for local running only")
class Scala3ImportedParserTest_RegeneratePsi extends TestCase

object Scala3ImportedParserTest_RegeneratePsi {
  val dottyParserTestsSuccessDir: String = Scala3ImportedParserTest_Move_Fixed_Tests.dottyParserTestsSuccessDir

  /**
   * Run this main method to move all scala 3 test files that generate no PsiErrorElements anymore to
   * the succeeding directory
   *
   * Use this if you have made progress in the parser and fixed files that produced PsiErrorElement
   * and, now, make Scala3ImportedParserTest_Fail fail. In this case this method will move those
   * into the succeeding folder, so they can fail if someone screws anything up in the parser, that
   * had previously worked.
   */
  def suite(): Test = new Scala3ImportedParserTest_RegeneratePsi

  @Ignore("for local running only")
  class Scala3ImportedParserTest_RegeneratePsi
    extends Scala3ImportedParserTestBase(Scala3ImportedParserTest.directory) {

    protected override def transform(testName: String, fileText: String, project: Project): String = {
      val (errors, file) = findErrorElements(fileText, project)
      val interlaced = findInterlacedRanges(file, testName)

      if (errors.isEmpty && interlaced.isEmpty) {
        val pathString = dottyParserTestsSuccessDir + "/" + testName + ".test"
        val path = Paths.get(pathString)

        println("Regenerate " + pathString)
        val psiTreeText = psiToString(file, true).replace(": " + file.name, "")
        val content = Files.readString(path)
        val searchString = "-----\n"
        val idx = content.indexOf(searchString).ensuring(_ >= 0)
        val newContent = content.substring(0, idx + searchString.length) + psiTreeText
        Files.writeString(path, newContent)
      }

      ""
    }

    override protected def transformExpectedResult(text: String): String = {
      ""
    }

    override protected def shouldHaveErrors: Boolean = throw new UnsupportedOperationException
  }
}
