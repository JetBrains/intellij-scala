package scalap

import java.io.{File => jFile}

import junit.framework.TestCase
import org.junit.Assert

import scala.tools.nsc.io.File

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
    val file = new File(new jFile(classFilePath))
    //facilitate working directory equal to 'scala-plugin' root directory as well
    val (bytes, expectedFilePath) = if (file.exists) (file.toByteArray(), classFilePath + ".test") else {
      val insidePath = s"testdata${separator}decompiler$separator$name$separator$fileName"
      (new File(new jFile(insidePath)).toByteArray(), insidePath + ".test")
    }

    val expectedResult = new File(new jFile(expectedFilePath)).slurp().replace("\r","")

    Decompiler.decompile(fileName, bytes) match {
      case Some((_, text)) =>
        Assert.assertEquals(expectedResult, text)
    }
  }

  def testPackageObject(): Unit = {
    doTest("package.class")
  }

  def testAnnotationArrayArguments(): Unit = {
    doTest("FlatSpecLike.class")
  }

  def testAnnotationArguments(): Unit = {
    doTest("AnnotArgTest.class")
  }

  def testScl9400(): Unit = {
    doTest("KMeansModel.class")
  }
}
