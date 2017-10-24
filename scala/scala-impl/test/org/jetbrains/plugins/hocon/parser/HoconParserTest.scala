package org.jetbrains.plugins.hocon
package parser

import com.intellij.psi.impl.DebugUtil.psiToString
import org.junit.runner.RunWith
import org.junit.runners.AllTests

@RunWith(classOf[AllTests])
class HoconParserTest extends HoconFileSetTestCase("parser") {

  override protected def transform(data: Seq[String]): String = {
    val psiFile = HoconFileSetTestCase.createPseudoPhysicalHoconFile(data.head)
    psiToString(psiFile, false).replace(":" + psiFile.getName, "")
  }
}

object HoconParserTest extends TestSuiteCompanion[HoconParserTest]
