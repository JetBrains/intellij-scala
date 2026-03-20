package org.jetbrains.plugins.scala.intelliLang.injection

import com.intellij.patterns.PsiJavaPatterns
import org.intellij.plugins.intelliLang.inject.config.InjectionPlace
import org.jetbrains.plugins.scala.patterns.ScalaPatterns

//SCL-24947
class ScalaLanguageInjectorTest_CallArgumentPattern extends ScalaLanguageInjectorTest_CallArgumentPatternBase {

  override def setUp(): Unit = {
    super.setUp()
    val methodPattern = PsiJavaPatterns.psiMethod().withName("myMethod").definedInClass("A")
    registerRegexpCallArgumentPattern(new InjectionPlace(ScalaPatterns.scalaLiteral().callArgument(0, methodPattern), true))
  }

  def testPatternInjection_CallArgument_RegularMethodCall(): Unit = {
    doRegexpInjectionTest(
      s"""class A {
         |  def myMethod(pattern: String): Unit = ???
         |}
         |new A().myMethod("[0-9]+")
         |""".stripMargin,
      "[0-9]+"
    )
  }

  // SCL-24947: language injection should also work when the method is called with type arguments
  def testPatternInjection_CallArgument_GenericMethodCall(): Unit = {
    doRegexpInjectionTest(
      s"""class A {
         |  def myMethod[T](pattern: String): T = ???
         |}
         |new A().myMethod[String]("[0-9]+")
         |""".stripMargin,
      "[0-9]+"
    )
  }
}
