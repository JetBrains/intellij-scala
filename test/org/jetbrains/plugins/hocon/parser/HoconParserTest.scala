package org.jetbrains.plugins.hocon.parser

import com.intellij.psi.impl.DebugUtil
import org.jetbrains.plugins.hocon.{HoconTestUtils, TestSuiteCompanion}
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.runner.RunWith
import org.junit.runners.AllTests

object HoconParserTest extends TestSuiteCompanion[HoconParserTest]

@RunWith(classOf[AllTests])
class HoconParserTest extends BaseScalaFileSetTestCase(TestUtils.getTestDataPath + "/hocon/parser/data") {
  def transform(testName: String, data: Array[String]) = {
    val psiFile = HoconTestUtils.createPseudoPhysicalHoconFile(getProject, data(0))
    DebugUtil.psiToString(psiFile, false).replace(":" + psiFile.getName, "")
  }
}
