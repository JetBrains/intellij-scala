package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.impl.DebugUtil.psiToString
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

  protected override def transform(testName: String, fileText: String, project: Project): String = {
    val lightFile = createLightFile(fileText, project)

    val errors = lightFile
      .elements
      .collect { case error: PsiErrorElement => error }
      .toSeq

    val hasErrorElements = errors.nonEmpty

    if (hasErrorElements != shouldHaveErrorElements) {
      println(fileText)
      println("-------")
      println(psiToString(lightFile, false).replace(": " + lightFile.getName, ""))
    }

    val msg = s"Found following errors: " + errors.mkString(", ")

    if (shouldHaveErrorElements) {
      assertTrue("Was expected to find error elements, but found none", hasErrorElements)
    } else {
      assertFalse(msg, hasErrorElements)
    }

    // TODO: return real psi tree instead of empty one and add reference tree to testfiles
    ""
  }

  protected def shouldHaveErrorElements: Boolean

  override protected def shouldPass = true
}


class Scala3ImportedParserTest extends Scala3ImportedParserTestBase("/parser/scala3Import/success") {
  override protected def shouldHaveErrorElements: Boolean = false
}

object Scala3ImportedParserTest {
  def suite = new Scala3ImportedParserTest()
}




class Scala3ImportedParserTest_Fail extends Scala3ImportedParserTestBase("/parser/scala3Import/fail") {
  override protected def shouldHaveErrorElements: Boolean = true
}

object Scala3ImportedParserTest_Fail {
  def suite = new Scala3ImportedParserTest_Fail()
}