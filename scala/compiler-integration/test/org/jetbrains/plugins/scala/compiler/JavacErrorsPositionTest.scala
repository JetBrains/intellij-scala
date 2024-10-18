package org.jetbrains.plugins.scala.compiler

import com.intellij.compiler.CompilerMessageImpl
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithJdkVersions, RunWithScalaVersions, TestJdkVersion, TestScalaVersion}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

import scala.jdk.CollectionConverters._

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_13))
@RunWithJdkVersions(Array(
  TestJdkVersion.JDK_1_8,
  TestJdkVersion.JDK_11,
  TestJdkVersion.JDK_17
))
@Category(Array(classOf[CompilationTests]))
abstract class JavacErrorPositionsTestBase(
  override protected val incrementalityType: IncrementalityType
) extends ScalaCompilerTestBase {

  override protected def buildProcessJdk: Sdk = CompileServerLauncher.defaultSdk(getProject)

  def testJavacErrorsPosition(): Unit = {
    IdeaTestUtil.setProjectLanguageLevel(getProject, LanguageLevel.JDK_1_8)

    addFileToProjectSources("StringFactorial.java",
      """import java.math.BigInteger;
        |
        |public final class StringFactorial {
        |    public static String factorial(String n) {
        |        BigInteger fact = BigInteger.ONE;
        |        for (BigInteger N = new BigInteger(n); N.compareTo(BigInteger.ZERO) > 0; N--) {
        |            fact *= N;
        |        }
        |        return fact;
        |    }
        |}
        |""".stripMargin)

    val errors = compiler.make().asScala.toSeq.filter(_.getCategory == CompilerMessageCategory.ERROR)
    assertEquals(3, errors.size)

    val Seq(error1, error2, error3) = errors.map { case impl: CompilerMessageImpl => impl }

    val message1 = error1.getMessage
    assertTrue(message1.contains("bad operand type") && message1.contains("BigInteger for unary operator '--'"))
    assertEquals(6, error1.getLine)
    assertEquals(83, error1.getColumn)

    assertTrue(error2.getMessage.contains("bad operand types for binary operator '*'"))
    assertEquals(7, error2.getLine)
    assertEquals(18, error2.getColumn)

    val message3 = error3.getMessage
    assertTrue(message3.contains("incompatible types: ") && message3.contains("BigInteger cannot be converted to ") && message3.contains("String"))
    assertEquals(9, error3.getLine)
    assertEquals(16, error3.getColumn)
  }
}

class JavacErrorPositionsTest_Zinc extends JavacErrorPositionsTestBase(IncrementalityType.SBT)

class JavacErrorsPositionsTest_IDEA extends JavacErrorPositionsTestBase(IncrementalityType.IDEA)
