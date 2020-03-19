package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.DebugUtil.psiToString
import com.intellij.psi.{PsiErrorElement, PsiFile}
import org.jetbrains.plugins.scala.base.ScalaFileSetTestCase
import org.jetbrains.plugins.scala.extensions._
import org.junit.Assert._
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.AllTests

@RunWith(classOf[AllTests])
@Category(Array(classOf[PerfCycleTests]))
abstract class Scala3ImportedParserTestBase(dir: String) extends ScalaFileSetTestCase(dir) {
  override protected def getLanguage: Language = Scala3Language.INSTANCE

  protected def findErrorElements(fileText: String, project: Project): (Seq[PsiErrorElement], PsiFile) = {
    val lightFile = createLightFile(fileText, project)
    val errors = lightFile
      .elements
      .collect { case error: PsiErrorElement => error }
      .toSeq

    errors -> lightFile
  }

  protected override def transform(testName: String, fileText: String, project: Project): String = {
    val (errors, lightFile) = findErrorElements(fileText, project)
    val hasErrorElements = errors.nonEmpty

    lazy val expected = psiToString(lightFile, false).replace(": " + lightFile.name, "")
    if (hasErrorElements != shouldHaveErrorElements) {
      println(fileText)
      println("-------")
      println(expected)
    }

    val msg = s"Found following errors: " + errors.mkString(", ")

    if (shouldHaveErrorElements) {
      assertTrue("Was expected to find error elements, but found none", hasErrorElements)
    } else {
      assertFalse(msg, hasErrorElements)
    }

    // TODO: return real psi tree instead of empty one and add reference tree to testfiles
    if (testsWithPsiResult.contains(testName))
      expected
    else ""
  }

  protected def testsWithPsiResult: Set[String] = Set.empty

  protected def shouldHaveErrorElements: Boolean

  override protected def shouldPass = true
}


class Scala3ImportedParserTest extends Scala3ImportedParserTestBase(Scala3ImportedParserTest.directory) {
  override protected def shouldHaveErrorElements: Boolean = false

  override val testsWithPsiResult = Set(
    "i7217", "i7648", "i7428", "i7757", "i4561",
    "reference_auto-param-tupling"
  )
}

object Scala3ImportedParserTest {
  val directory = "/parser/scala3Import/success"
  def suite = new Scala3ImportedParserTest()
}


/**
 * If this tests fails because you fixed parser stuff,
 * run [[Scala3ImportedParserTest_Move_Fixed_Tests]].
 */
class Scala3ImportedParserTest_Fail extends Scala3ImportedParserTestBase(Scala3ImportedParserTest_Fail.directory) {
  override protected def shouldHaveErrorElements: Boolean = true
}

object Scala3ImportedParserTest_Fail {
  val directory = "/parser/scala3Import/fail"
  def suite = new Scala3ImportedParserTest_Fail()
}
