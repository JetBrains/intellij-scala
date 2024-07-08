package org.jetbrains.plugins.scala.compiler.zinc

import com.intellij.compiler.CompilerMessageImpl
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters._

@Category(Array(classOf[CompilationTests]))
class JavacErrorsPositionTest extends ZincTestBase {

  def testJavacErrorsPosition_Zinc(): Unit = {
    runJavacErrorsPositionTest(IncrementalityType.SBT)
  }

  def testJavacErrorsPosition_IDEA(): Unit = {
    runJavacErrorsPositionTest(IncrementalityType.IDEA)
  }

  private def runJavacErrorsPositionTest(incrementality: IncrementalityType): Unit = {
    createProjectSubDirs("project", "src/main/java")
    createProjectSubFile("project/build.properties", "sbt.version=1.10.1")
    createProjectSubFile("src/main/java/StringFactorial.java",
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
    createProjectConfig(
      """lazy val root = project.in(file("."))
        |  .settings(scalaVersion := "2.13.14")
        |""".stripMargin)

    importProject(false)
    ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType = incrementality
    val modules = ModuleManager.getInstance(myProject).getModules
    rootModule = modules.find(_.getName == "root").orNull
    compiler = new CompilerTester(myProject, java.util.Arrays.asList(modules: _*), null, false)

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
