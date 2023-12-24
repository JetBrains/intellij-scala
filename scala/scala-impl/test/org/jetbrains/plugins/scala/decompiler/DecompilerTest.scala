package org.jetbrains.plugins.scala.decompiler

import junit.framework.TestCase
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert

import java.io.{File => jFile}
import scala.io.Codec
import scala.tools.nsc.io.File

// TODO Use .sig files rather than .class files.
trait DecompilerTestBase extends TestCase {
  def basePath: String = s"${TestUtils.getTestDataPath}/decompiler"

  def doTest(fileName: String): Unit = {
    val classFilePath = getClassFilePath(fileName, getName)
    val expectedFilePath: String = classFilePath + ".test"

    val expectedResult = new File(new jFile(expectedFilePath))(Codec.UTF8).slurp().replace("\r","").trim.replace("_root_.scala.Predef", "scala.Predef")

    Assert.assertEquals(expectedResult, decompile(classFilePath).replace("_root_.", ""))
  }

  protected def getClassFilePath(fileName: String, testName: String = "") = {
    val name = if (testName.isEmpty) "" else {
      assert(testName.startsWith("test") && testName.length > 4)
      testName(4).toLower.toString + testName.substring(5)
    }
    val dirPath: String = s"$basePath/$name"
    s"$dirPath/$fileName"
  }

  protected final def decompile(classFilePath: String): String = {
    val file = new File(new jFile(classFilePath))(Codec.UTF8)
    val Some((_, sourceText)) = Decompiler.sourceNameAndText(file.name, file.toByteArray())
    sourceText
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

  def testScl10858(): Unit = {
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

  def testScl5865(): Unit = {
    doTest("$colon$colon.class")
  }

  def testSuperInner(): Unit = {
    doTest("ProcessBuilderImpl.class")
  }

  def testScl8251(): Unit = {
    doTest("LinkedEntry.class")
  }

  def testScl7997(): Unit = {
    doTest("CommentDecompilation.class")
  }

  def testConsumerActorRouteBuilder(): Unit = {
    doTest("ConsumerActorRouteBuilder.class")
  }

  def testFuture_2_13(): Unit = {
    doTest("Future.class")
  }

  def testScl13744(): Unit = {
    doTest("BrokenUsage.class")
  }

  def testPrivateCompanion(): Unit = {
    doTest("RequestBuilder.class")
  }

  def testLiteralTypes(): Unit = {
    doTest("LiteralTypes.class")
  }

  def testConstants(): Unit = {
    doTest("Constants210.class")
    doTest("Constants213.class")
  }

  def testJavaConstants(): Unit = {
    doTest("Double.class")
    doTest("Float.class")
  }

  def testBacktickedPaths(): Unit = {
    doTest("MediaTypes.class")
  }

  def testAbstractOverride(): Unit = {
    doTest("LoggingFSM.class")
  }

  def testDistageTestConfig(): Unit = {
    doTest("TestConfig.class")
  }

  def testContextBounds(): Unit = {
    doTest("ContextBounds.class")
  }

  def testScl20248(): Unit = doTest("Animal.class")
}
