package org.jetbrains.plugins.scala.decompiler

import java.io.{File => jFile}

import junit.framework.TestCase
import org.junit.Assert

import scala.tools.nsc.io.File

/**
 * @author Alefas
 * @since  11/09/15
 */
trait DecompilerTestBase extends TestCase {
  def basePath(separator: Char) = s"testdata${separator}decompiler$separator"

  def doTest(fileName: String): Unit = {
    val classFilePath = getClassFilePath(fileName, getName)
    val expectedFilePath: String = classFilePath + ".test"

    val expectedResult = new File(new jFile(expectedFilePath)).slurp().replace("\r","")

    Assert.assertEquals(expectedResult, decompile(classFilePath))
  }

  protected def getClassFilePath(fileName: String, testName: String = "") = {
    val name = if (testName.isEmpty) "" else {
      assert(testName.startsWith("test") && testName.length > 4)
      testName(4).toLower + testName.substring(5)
    }
    val separator = jFile.separatorChar
    val dirPath: String = {
      val path = basePath(separator) + {if (name.isEmpty) "" else name + separator}
      val communityDir = new jFile(path)
      if (communityDir.exists()) path
      else s"scala-plugin$separator$path"
    }
    s"$dirPath$separator$fileName"
  }

  protected def decompile(classFilePath: String): String = {
    val file = new File(new jFile(classFilePath))
    val bytes = file.toByteArray()
    Decompiler.decompile(file.name, bytes).map(_._2).get
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

  def testScl9877(): Unit = {
    doTest("MyJoin.class")
  }

  def testScl9877_1(): Unit = {
    doTest("MyJoin.class")
  }

  //SCL-10244, SCL-10252
  def testSelfType(): Unit = {
    doTest("ResultExtractors.class")
  }

  def testScl10858() = {
    doTest("LazyValBug.class")
  }

  def testPackageName(): Unit = {
    doTest("Bactickeds.class")
  }

  def testScalaLongSignature(): Unit = {
    doTest("Generated.class")
  }

  def testTransientAnnotation(): Unit = {
    doTest("AnyValManifest.class")
  }

  def testDuplicatedAnnotation(): Unit = {
    doTest("AssemblyBuilder.class")
  }

  def testHashTable(): Unit = {
    doTest("HashTable.class")
  }

  def testOverloadedMethodsAnnotations(): Unit = {
    doTest("StringLike.class")
  }

  def testSbtSessionSettings(): Unit = {
    doTest("SessionSettings.class")
  }

  def testScl12271(): Unit = {
    doTest("DefaultEntry.class")
  }

  def testScl5865() = {
    doTest("$colon$colon.class")
  }

  def testSuperInner() = {
    doTest("ProcessBuilderImpl.class")
  }

  def testScl8251() = {
    doTest("LinkedEntry.class")
  }

  def testScl7997() = {
    doTest("CommentDecompilation.class")
  }
}
