package scala.tools.scalap

import java.io.{File => jFile}

import junit.framework.TestCase
import org.junit.Assert

import scala.tools.nsc.io.File

/**
 * @author Alefas
 * @since  11/09/15
 */
abstract class DecompilerTestBase extends TestCase {
  def basePath(separator: Char) = s"testdata${separator}decompiler$separator"

  def doTest(fileName: String): Unit = {
    val testName = getName
    assert(testName.startsWith("test") && testName.length > 4)
    val name = testName(4).toLower + testName.substring(5)

    val separator = jFile.separatorChar
    val dirPath: String = {
      val path = s"${basePath(separator)}$name$separator"
      val communityDir = new jFile(path)
      if (communityDir.exists()) path
      else s"scala-plugin$separator$path"
    }
    val classFilePath = s"$dirPath$separator$fileName"
    val expectedFilePath: String = classFilePath + ".test"

    val file = new File(new jFile(classFilePath))
    val bytes = file.toByteArray()

    val expectedResult = new File(new jFile(expectedFilePath)).slurp().replace("\r","")

    (Decompiler.decompile(fileName, bytes): @unchecked)match {
      case Some((_, text)) =>
        Assert.assertEquals(expectedResult, text)
    }
  }
}

class DecompilerTest extends DecompilerTestBase {

  def testPackageObject(): Unit = {
    doTest("package.class")
  }

  def testAnnotationArrayArguments(): Unit = {
    doTest("FlatSpecLike.class")
  }

  def testAnnotationArguments(): Unit = {
    doTest("AnnotArgTest.class")
  }

  def testScl9394(): Unit = {
    doTest("package.class")
  }

  def testScl9400(): Unit = {
    doTest("KMeansModel.class")
  }

  def testScl9419(): Unit = {
    doTest("$bar.class")
  }

  def testScl9457(): Unit = {
    doTest("AnyVaal212.class")
  }
}
