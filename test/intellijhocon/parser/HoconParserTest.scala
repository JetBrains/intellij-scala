package intellijhocon
package parser

import com.intellij.psi.impl.DebugUtil
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase
import org.jetbrains.plugins.scala.util.TestUtils

class HoconParserTest extends BaseScalaFileSetTestCase(TestUtils.getTestDataPath + "/hocon/parser/data") {
  def transform(testName: String, data: Array[String]) = {
    val psiFile = HoconTestUtils.createPseudoPhysicalHoconFile(getProject, data(0))
    DebugUtil.psiToString(psiFile, false).replace(":" + psiFile.getName, "")
  }
}
