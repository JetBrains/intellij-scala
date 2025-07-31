package org.jetbrains.plugins.scala.highlighter.usages

import com.intellij.codeInsight.highlighting.{HighlightUsagesHandler, HighlightUsagesHandlerBase}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class Scala3HighlightUsagesHandlerTest extends ScalaHighlightUsagesHandlerTestBase {

  override protected def supportedIn(version: ScalaVersion) = version == LatestScalaVersions.Scala_3_0

  override def createHandler: HighlightUsagesHandlerBase[PsiElement] =
    HighlightUsagesHandler.createCustomHandler(getEditor, getFile).asInstanceOf[HighlightUsagesHandlerBase[PsiElement]]

  def testCompanionAbstractType1(): Unit = doTest(
    s"""
       |type$CARET Foo
       |object Foo
      """.stripMargin, Seq("type", "object"))

  def testCompanionAbstractType2(): Unit = doTest(
    s"""
       |type Foo
       |object$CARET Foo
      """.stripMargin, Seq("object", "type"))

  def testCompanionOpaqueType1(): Unit = doTest(
    s"""
       |opaque type$CARET Foo = Int
       |object Foo
      """.stripMargin, Seq("type", "object"))

  def testCompanionOpaqueType2(): Unit = doTest(
    s"""
       |opaque type Foo = Int
       |object$CARET Foo
      """.stripMargin, Seq("object", "type"))

  def testCompanionTypeAlias1(): Unit = doTest(
    s"""
       |type$CARET Foo = Int
       |object Foo
      """.stripMargin, Seq.empty)

  def testCompanionTypeAlias2(): Unit = doTest(
    s"""
       |type Foo = Int
       |object$CARET Foo
      """.stripMargin, Seq.empty)
}
