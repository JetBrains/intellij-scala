package org.jetbrains.plugins.scala.codeInsight.intentions.implementAbstract

import org.jetbrains.plugins.scala.codeInsight.intention.ImplementAbstractMethodIntention
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

abstract class ImplementAbstractMethodTestBase extends ScalaIntentionTestBase {
  override def familyName: String = new ImplementAbstractMethodIntention().getFamilyName

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

  def testWithPackageNonEmptyTemplateBody(): Unit = {
    val text =
      s"""package test
         |
         |trait MyTrait {
         |  def fo${CARET}o1(i: Int): Either[String, Unit] // invoke "Implement method foo1"
         |}
         |class MyTraitImpl() extends MyTrait {}
         |""".stripMargin
    val result =
      s"""package test
         |
         |trait MyTrait {
         |  def foo1(i: Int): Either[String, Unit] // invoke "Implement method foo1"
         |}
         |
         |class MyTraitImpl() extends MyTrait {
         |  override def foo1(i: Int): Either[String, Unit] = $START???$END
         |}
         |""".stripMargin
    doTest(text, result)
  }

  def testWithPackageNonEmptyTemplateBody2(): Unit = {
    val text =
      s"""package test
         |
         |trait MyTrait {
         |  def foo(i: Int): Either[String, Unit]
         |  def fo${CARET}o1(i: Int): Either[String, Unit] // invoke "Implement method foo1"
         |}
         |class MyTraitImpl() extends MyTrait {
         |
         |  override def foo(i: Int): Either[String, Unit] =
         |    Left("")
         |}
         |""".stripMargin
    val result =
      s"""package test
         |
         |trait MyTrait {
         |  def foo(i: Int): Either[String, Unit]
         |
         |  def foo1(i: Int): Either[String, Unit] // invoke "Implement method foo1"
         |}
         |
         |class MyTraitImpl() extends MyTrait {
         |
         |  override def foo(i: Int): Either[String, Unit] =
         |    Left("")
         |
         |  override def foo1(i: Int): Either[String, Unit] = $START???$END
         |}
         |""".stripMargin
    doTest(text, result)
  }
}

class ImplementAbstractMethodTest extends ImplementAbstractMethodTestBase {
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

  def testWithPackageEmptyTemplateBody(): Unit = {
    val text =
      s"""package test
         |
         |trait MyTrait {
         |  def fo${CARET}o1(i: Int): Either[String, Unit] // invoke "Implement method foo1"
         |}
         |class MyTraitImpl() extends MyTrait
         |""".stripMargin
    val result =
      s"""package test
         |
         |trait MyTrait {
         |  def foo1(i: Int): Either[String, Unit] // invoke "Implement method foo1"
         |}
         |
         |class MyTraitImpl() extends MyTrait {
         |  override def foo1(i: Int): Either[String, Unit] = $START???$END
         |}
         |""".stripMargin
    doTest(text, result)
  }
}

final class ImplementAbstractMethodTest_Scala3 extends ImplementAbstractMethodTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3

  def testWithPackageEmptyTemplateBody_IndentationBasedSyntax(): Unit = {
    val text =
      s"""package test
         |
         |trait MyTrait:
         |  def fo${CARET}o1(i: Int): Either[String, Unit] // invoke "Implement method foo1"
         |class MyTraitImpl() extends MyTrait
         |""".stripMargin
    val result =
      s"""package test
         |
         |trait MyTrait:
         |  def foo1(i: Int): Either[String, Unit] // invoke "Implement method foo1"
         |
         |class MyTraitImpl() extends MyTrait:
         |  override def foo1(i: Int): Either[String, Unit] = $START???$END
         |""".stripMargin
    doTest(text, result)
  }

  // keep existing template body
  def testWithPackageNonEmptyTemplateBody_IndentationBasedSyntax(): Unit = {
    val text =
      s"""package test
         |
         |trait MyTrait:
         |  def fo${CARET}o1(i: Int): Either[String, Unit] // invoke "Implement method foo1"
         |class MyTraitImpl() extends MyTrait {}
         |""".stripMargin
    val result =
      s"""package test
         |
         |trait MyTrait:
         |  def foo1(i: Int): Either[String, Unit] // invoke "Implement method foo1"
         |
         |class MyTraitImpl() extends MyTrait {
         |  override def foo1(i: Int): Either[String, Unit] = $START???$END
         |}
         |""".stripMargin
    doTest(text, result)
  }

  def testWithPackageNonEmptyTemplateBody2_IndentationBasedSyntax(): Unit = {
    val text =
      s"""package test
         |
         |trait MyTrait:
         |  def foo(i: Int): Either[String, Unit]
         |  def fo${CARET}o1(i: Int): Either[String, Unit] // invoke "Implement method foo1"
         |class MyTraitImpl() extends MyTrait:
         |
         |  override def foo(i: Int): Either[String, Unit] =
         |    Left("")
         |""".stripMargin
    val result =
      s"""package test
         |
         |trait MyTrait:
         |  def foo(i: Int): Either[String, Unit]
         |
         |  def foo1(i: Int): Either[String, Unit] // invoke "Implement method foo1"
         |
         |class MyTraitImpl() extends MyTrait:
         |
         |  override def foo(i: Int): Either[String, Unit] =
         |    Left("")
         |
         |  override def foo1(i: Int): Either[String, Unit] = $START???$END
         |""".stripMargin
    doTest(text, result)
  }
}
