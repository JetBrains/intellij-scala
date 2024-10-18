package org.jetbrains.plugins.scala.compiler

import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.JavaCompilerConfigurationProxy
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.assertNoErrorsOrWarnings
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithJdkVersions, RunWithScalaVersions, TestJdkVersion, TestScalaVersion}
import org.junit.Assert.{assertEquals, assertNotNull}
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

import java.net.URLClassLoader
import scala.jdk.CollectionConverters._

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
@RunWithJdkVersions(Array(
  TestJdkVersion.JDK_1_8,
  TestJdkVersion.JDK_11,
  TestJdkVersion.JDK_17
))
@Category(Array(classOf[CompilationTests]))
abstract class JavacOptionsTestBase(
  override protected val incrementalityType: IncrementalityType
) extends ScalaCompilerTestBase {

  def testJavacOptions_Parameters(): Unit = {
    IdeaTestUtil.setProjectLanguageLevel(getProject, LanguageLevel.JDK_1_8)
    JavaCompilerConfigurationProxy.setAdditionalOptions(getProject, getModule, java.util.Collections.singletonList("-parameters"))

    addFileToProjectSources("org/example/Foo.java",
      """package org.example;
        |
        |public class Foo {
        |  public int getInt(boolean email) {
        |    return email ? 1 : 0;
        |  }
        |}
        |""".stripMargin)

    val messages = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages)

    val fooClassFile = findClassFile("org.example.Foo")
    assertNotNull("Could not find class file 'org.example.Foo' in 'module1'", fooClassFile)

    val classLoader = new URLClassLoader(Array(fooClassFile.getParent.getParent.getParent.toUri.toURL))
    val fooClass = Class.forName("org.example.Foo", true, classLoader)

    val fooParameter = fooClass.getMethods.find(_.getName == "getInt").orNull.getParameters.head.getName
    assertEquals("Wrong compiled parameter name in class 'org.example.Foo'", "email", fooParameter)
  }

  def testJavacOptions_NoParameters(): Unit = {
    IdeaTestUtil.setProjectLanguageLevel(getProject, LanguageLevel.JDK_1_8)
    JavaCompilerConfigurationProxy.setAdditionalOptions(getProject, getModule, java.util.Collections.emptyList())

    addFileToProjectSources("org/example/Bar.java",
      """package org.example;
        |
        |public class Bar {
        |  public int getInt(boolean email) {
        |    return email ? 1 : 0;
        |  }
        |}
        |""".stripMargin)

    val messages = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages)

    val barClassFile = findClassFile("org.example.Bar")
    assertNotNull("Could not find class file 'org.example.Bar' in 'module2'", barClassFile)

    val classLoader = new URLClassLoader(Array(barClassFile.getParent.getParent.getParent.toUri.toURL))
    val barClass = Class.forName("org.example.Bar", true, classLoader)

    val barParameter = barClass.getMethods.find(_.getName == "getInt").orNull.getParameters.head.getName
    assertEquals("Wrong compiled parameter name in class 'org.example.Bar'", "arg0", barParameter)
  }
}

class JavacOptionsTest_Zinc extends JavacOptionsTestBase(IncrementalityType.SBT)

class JavacOptionsTest_IDEA extends JavacOptionsTestBase(IncrementalityType.IDEA)
