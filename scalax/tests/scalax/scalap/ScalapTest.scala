package scalax.scalap

import _root_.junit.framework.TestCase
import java.io._
import java.net.{URLClassLoader, URL}
import rules.scalasig.{ClassFileParser, ScalaSigAttributeParsers, ScalaSigPrinter, ByteCode}
import util.ScalaxTestUtil
import util.ScalaxTestUtil._

/**
 * @author ilyas
 */

class ScalapTest extends TestCase {
  def getTestDataPath = ScalaxTestUtil.getTestDataPath + "/scalap/"

  def doTest() {
    val className = ScalaxTestUtil.getTestName(getName, false)
    val testFolderPath = getTestDataPath + getTestName(getName, true) + "/"
    val file = new File(testFolderPath)
    val url = file.toURI.toURL
    val loader = new URLClassLoader(Array(url), getClass.getClassLoader)
    assert(true)
    val clazz = loader.loadClass(className)
    val resultFile = new File(testFolderPath + "result.test")
    val in = new BufferedReader(new FileReader(resultFile))
    var line: String = null
    val buffer = new StringBuffer

    val result = decompileFile(clazz)

    do {
      line = in.readLine
      if (line != null) buffer.append(line + "\n")
    } while (line != null)
    in.close

    println(result)

    val expected = buffer.toString
    assert(result == expected)
  }

  def decompileFile(clazz: Class[_]) = {
    val byteCode = ByteCode.forClass(clazz)
    val classFile = ClassFileParser.parse(byteCode)
    val Some(sig) = classFile.attribute("ScalaSig").map(_.byteCode).map(ScalaSigAttributeParsers.parse)
    val baos = new ByteArrayOutputStream
    val stream = new PrintStream(baos)
    val printer = new ScalaSigPrinter(stream)
    for (c <- sig.topLevelClasses) printer.printSymbol(c)
    for (c <- sig.topLevelObjects) printer.printSymbol(c)
    baos.toString
  }

  def testAbstractClass = doTest

  def testCbnParam = doTest

  def testClassWithExistential = doTest

  def testClassWithSelfAnnotation = doTest

  def testImplicitParam = doTest

  def testSequenceParam = doTest

  def testSimpleClass = doTest

  def testWildcardType = doTest

  def testParamClauses = doTest

  def testCaseClass = doTest

  def testTraitObject = doTest

  def testCovariantParam = doTest

  def testParamNames = doTest

}