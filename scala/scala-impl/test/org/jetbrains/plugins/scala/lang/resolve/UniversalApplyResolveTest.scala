package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase.{REFSRC, REFTGT}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class UniversalApplyResolveTest extends SimpleResolveTestBase {
  override protected def supportedIn(version: ScalaVersion) = version.isScala3

  override protected def getTgt(source: String, file: PsiFile)(implicit opts: SrcTgtOptions): PsiElement = {
    val target = super.getTgt(source, file)
    target.parentOfType[ScPrimaryConstructor].getOrElse(target)
  }

  def testApplyResolveOnCall(): Unit = {
    doResolveTest(
      s"""
         |class Foo$REFTGT(i: Int)
         |
         |object Test {
         |  Foo.ap${REFSRC}ply(1)
         |}
      """.stripMargin)
  }

  def testApplyResolveOnGenericCall(): Unit = {
    doResolveTest(
      s"""
         |class Foo[T]$REFTGT(t: T)
         |
         |object Test {
         |  Foo.ap${REFSRC}ply[T](1)
         |}
      """.stripMargin)
  }

  def testApplyResolveOnInfixCall(): Unit = {
    doResolveTest(
      s"""
         |class ${REFTGT}Foo(i: Int)
         |
         |object Test {
         |  Foo ap${REFSRC}ply 1
         |}
      """.stripMargin)
  }

  def testApplyResolveWithoutCall(): Unit = {
    doResolveTest(
      s"""
         |class Foo$REFTGT(i: Int)
         |
         |object Test {
         |  Foo.ap${REFSRC}ply
         |}
      """.stripMargin)
  }

  def testApplyResolveWithoutCallButTypeArgs(): Unit = {
    doResolveTest(
      s"""
         |class Foo[T]$REFTGT(t: T)
         |
         |object Test {
         |  Foo.ap${REFSRC}ply[Int]
         |}
      """.stripMargin)
  }

  def tesObjectResolveOnCall(): Unit = {
    doResolveTest(
      s"""
         |class ${REFTGT}Foo(i: Int)
         |
         |object Test {
         |  Fo${REFSRC}o.apply(1)
         |}
      """.stripMargin)
  }

  def testObjectResolveOnGenericCall(): Unit = {
    doResolveTest(
      s"""
         |class ${REFTGT}Foo[T](t: T)
         |
         |object Test {
         |  Fo${REFSRC}o.apply[T](1)
         |}
      """.stripMargin)
  }

  def testObjectResolveOnInfixCall(): Unit = {
    doResolveTest(
      s"""
         |class ${REFTGT}Foo(i: Int)
         |
         |object Test {
         |  F${REFSRC}oo apply 1
         |}
      """.stripMargin)
  }

  def testObjectResolveWithoutCall(): Unit = {
    doResolveTest(
      s"""
         |class ${REFTGT}Foo(i: Int)
         |
         |object Test {
         |  F${REFSRC}oo.apply
         |}
      """.stripMargin)
  }

  def testObjectResolveWithoutCallButTypeArgs(): Unit = {
    doResolveTest(
      s"""
         |class ${REFTGT}Foo[T](t: T)
         |
         |object Test {
         |  F${REFSRC}oo.apply[Int]
         |}
      """.stripMargin)
  }
}
