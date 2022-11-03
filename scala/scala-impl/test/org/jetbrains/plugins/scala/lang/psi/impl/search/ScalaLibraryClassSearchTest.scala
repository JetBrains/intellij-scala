package org.jetbrains.plugins.scala.lang.psi.impl.search

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.{PsiClass, PsiNamedElement, PsiPackage}
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiClassExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.lang.navigation.GoToClassAndSymbolTestBase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

import scala.jdk.CollectionConverters.SeqHasAsJava

private[search] trait ScalaLibraryClassSearchTestBase extends ScalaLightCodeInsightFixtureTestCase {
  override protected def additionalLibraries: Seq[LibraryLoader] = List(
    IvyManagedLoader(
      "org.scalatest" %% "scalatest-funsuite" % "3.2.14",
      "dev.zio"       %% "zio"                % "2.0.2",
    ),
  )

  protected def name(element: Any): String = element match {
    case null                   => "<null>"
    case pkg: PsiPackage        => pkg.getQualifiedName
    case cls: PsiClass          => cls.qualifiedName
    case named: PsiNamedElement => named.name
    case _                      => element.toString
  }

  private val SCALATEST_FUNSUITE_PACKAGE          = "org.scalatest.funsuite"
  protected val SCALATEST_ANY_FUN_SUITE_FQN       = s"$SCALATEST_FUNSUITE_PACKAGE.AnyFunSuite"
  protected val SCALATEST_ANY_FUN_SUITE_NAME_PART = "AnyFunSuit"

  private val ZIO_PACKAGE         = "zio"
  protected val ZIO_APP_FQN       = s"$ZIO_PACKAGE.ZIOApp"
  protected val ZIO_APP_NAME_PART = "ZIOAp"

  protected case class ClassDescriptor(name: String, `package`: String, predicate: Any => Boolean) {
    val fqn: String = if (`package`.isEmpty) name else `package` + "." + name
  }

  protected val SCALATEST_DESCRIPTORS: Seq[ClassDescriptor] = Seq(
    ClassDescriptor("AnyFunSuite",            SCALATEST_FUNSUITE_PACKAGE, _.is[ScClass]),
    ClassDescriptor("AnyFunSuiteLike",        SCALATEST_FUNSUITE_PACKAGE, _.is[ScTrait]),
    ClassDescriptor("FixtureAnyFunSuite",     SCALATEST_FUNSUITE_PACKAGE, _.is[ScClass]),
    ClassDescriptor("FixtureAnyFunSuiteLike", SCALATEST_FUNSUITE_PACKAGE, _.is[ScTrait]),
  )

  protected val ZIO_DESCRIPTORS: Seq[ClassDescriptor] = Seq(
    ClassDescriptor("ZIOApp",        ZIO_PACKAGE, _.is[ScTrait]),
    ClassDescriptor("ZIOApp",        ZIO_PACKAGE, _.is[ScObject]),
    ClassDescriptor("ZIOAppDefault", ZIO_PACKAGE, _.is[ScTrait]),
    ClassDescriptor("ZIOAppDefault", ZIO_PACKAGE, _.is[ScObject]),
    ClassDescriptor("ZIOAppArgs",    ZIO_PACKAGE, _.is[ScClass]),
    ClassDescriptor("ZIOAppArgs",    ZIO_PACKAGE, _.is[ScObject]),
  )
}

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest,
))
class ScalaCompleteLibraryClassTest
  extends ScalaCompletionTestBase
    with ScalaLibraryClassSearchTestBase {
  private def checkLookupItems(fileText: String, expectedDescriptors: Seq[ClassDescriptor]): Unit = {
    val (_, lookupItems) = activeLookupWithItems(fileText, CompletionType.BASIC)
    val actual = lookupItems.toList.map(item => (item.getLookupString, name(item.getPsiElement)))
    val expected = expectedDescriptors.map(d => (d.name, d.fqn))
    UsefulTestCase.assertContainsElements(actual.asJava, expected: _*)
  }

  def testScalatestLookupItems(): Unit = {
    val fileText =
      s"""package tests
         |
         |class MyTest extends AnyFunSuit$CARET
         |""".stripMargin

    checkLookupItems(fileText, SCALATEST_DESCRIPTORS)
  }

  def testZioLookupItems(): Unit = {
    val fileText =
      s"""package com.example
         |
         |object Main extends ZIOA$CARET
         |""".stripMargin

    checkLookupItems(fileText, ZIO_DESCRIPTORS)
  }
}

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest
))
class ScalaGoToLibraryClassTest
  extends GoToClassAndSymbolTestBase
    with ScalaLibraryClassSearchTestBase {
  private def checkClassElements(text: String, expectedDescriptors: Seq[ClassDescriptor]): Unit = {
    val elements = gotoClassElements(text)
    val expected = expectedDescriptors.map(d => (d.predicate, d.fqn))
    super[GoToClassAndSymbolTestBase].checkContainExpected(elements, expected: _*)
  }

  def testGoToClassScalatestShortName(): Unit =
    checkClassElements(SCALATEST_ANY_FUN_SUITE_NAME_PART, SCALATEST_DESCRIPTORS)

  def testGoToClassScalatestFullName(): Unit =
    checkClassElements(SCALATEST_ANY_FUN_SUITE_FQN, SCALATEST_DESCRIPTORS)

  def testGoToClassZioShortName(): Unit =
    checkClassElements(ZIO_APP_NAME_PART, ZIO_DESCRIPTORS)

  def testGoToClassZioFullName(): Unit =
    checkClassElements(ZIO_APP_FQN, ZIO_DESCRIPTORS)
}
