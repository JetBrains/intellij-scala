package org.jetbrains.plugins.scala
package codeInsight.intentions.implementAbstract

import com.intellij.codeInsight.intention.impl.ImplementAbstractMethodAction
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings

class ImplementAbstractMethodTest extends ScalaIntentionTestBase {

  override def familyName: String = new ImplementAbstractMethodAction().getFamilyName

  def testFromTrait(): Unit = {
    val text =
      """
        |trait A {
        |  def <caret>f: Int
        |}
        |
        |class AA extends A""".stripMargin
    val result =
      s"""
        |trait A {
        |  def f: Int
        |}
        |
        |class AA extends A {
        |  override def f = $START???$END
        |}""".stripMargin
    
    TypeAnnotationSettings.set(getProject,
      TypeAnnotationSettings.noTypeAnnotationForPublic(TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject))))
    
    doTest(text, result)
  }

  def testFromAbstractClass(): Unit = {
    val text =
      """
        |abstract class A {
        |  def <caret>f: Int
        |}
        |
        |class AA extends A {}""".stripMargin
    val result =
      s"""
        |abstract class A {
        |  def f: Int
        |}
        |
        |class AA extends A {
        |  override def f: Int = $START???$END
        |}""".stripMargin
  
    TypeAnnotationSettings.set(getProject, TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject)))
    doTest(text, result)
  }

  def testParameterizedTrait(): Unit = {
    val text =
      """
        |trait A[T] {
        |  def <caret>f: T
        |}
        |
        |class AA extends A[Int] {}""".stripMargin
    val result =
      s"""
        |trait A[T] {
        |  def f: T
        |}
        |
        |class AA extends A[Int] {
        |  override def f: Int = $START???$END
        |}""".stripMargin
  
    TypeAnnotationSettings.set(getProject, TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject)))
    doTest(text, result)
  }

  def testFunDefInTrait(): Unit = {
    val text =
      """
        |trait A {
        |  def <caret>f: Int = 0
        |}
        |
        |class AA extends A
      """.stripMargin
  
    TypeAnnotationSettings.set(getProject, TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject)))
    checkIntentionIsNotAvailable(text)
  }

  def testUnitReturn(): Unit = {
    val text =
      """
        |trait A {
        |  def <caret>f
        |}
        |
        |class AA extends A""".stripMargin
    val result =
      s"""
        |trait A {
        |  def f
        |}
        |
        |class AA extends A {
        |  override def f: Unit = $START???$END
        |}""".stripMargin
  
    TypeAnnotationSettings.set(getProject, TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject)))
    doTest(text, result)
  }

}
