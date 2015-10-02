package scalap

import java.io.{File => jFile}

import junit.framework.TestCase
import org.junit.Assert

import scala.tools.nsc.io.File
import scala.tools.scalap.scalax.rules.scalasig.{ClassFileParser, ByteCode}

/**
 * @author Alefas
 * @since  11/09/15
 */
class DecompilerTest extends TestCase {
  def doTest(fileName: String): Unit = {
    val testName = getName
    assert(testName.startsWith("test") && testName.length > 4)
    val name = testName(4).toLower + testName.substring(5)

    val separator = jFile.separatorChar
    val classFilePath: String = s"scala-plugin${separator}testdata${separator}decompiler$separator$name$separator$fileName"
    val expectedFilePath: String = classFilePath + ".test"

    val file = new File(new jFile(classFilePath))
    val bytes = file.toByteArray()

    val expectedResult = new File(new jFile(expectedFilePath)).slurp()

    Decompiler.decompile(fileName, bytes) match {
      case Some((_, text)) =>
        Assert.assertEquals(expectedResult, text)
    }
  }

  def testPackageObject(): Unit = {
    doTest("package.class")
  }
}
