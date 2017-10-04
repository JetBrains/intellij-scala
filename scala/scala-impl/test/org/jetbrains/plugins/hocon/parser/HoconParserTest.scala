package org.jetbrains.plugins.hocon.parser

import com.intellij.psi.impl.DebugUtil
import org.jetbrains.plugins.hocon.{HoconFileSetTestCase, TestSuiteCompanion}
import org.junit.runner.RunWith
import org.junit.runners.AllTests

object HoconParserTest extends TestSuiteCompanion[HoconParserTest]

@RunWith(classOf[AllTests])
class HoconParserTest extends HoconFileSetTestCase("parser") {
  def transform(data: Seq[String]): String = {
    val psiFile = HoconFileSetTestCase.createPseudoPhysicalHoconFile(data.head)
    DebugUtil.psiToString(psiFile, false).replace(":" + psiFile.getName, "")
  }
}
