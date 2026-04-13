package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.Message.Error

class Scala3NamedTypeArgumentsAnnotatorTest extends ScalaHighlightingTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3

  def testMethodCallRequiresFeatureImport(): Unit = assertErrors(
    """
      |def construct[Elem, Coll[_]](xs: Elem*): Coll[Elem] = ???
      |
      |val xs = construct[Coll = List, Elem = Int](1, 2, 3)
      |""".stripMargin,
    Error("Coll", "Named type arguments require import scala.language.experimental.namedTypeArguments")
  )

  def testMethodCallWithFeatureImport(): Unit = assertNoErrors(
    """
      |import scala.language.experimental.namedTypeArguments
      |
      |def construct[Elem, Coll[_]](xs: Elem*): Coll[Elem] = ???
      |
      |val xs = construct[Coll = List, Elem = Int](1, 2, 3)
      |""".stripMargin
  )

  def testTypeConstructorNamedTypeArgsAreForbiddenEvenWithFeatureImport(): Unit = assertErrors(
    """
      |import scala.language.experimental.namedTypeArguments
      |
      |class C[T]
      |type X = C[T = Int]
      |""".stripMargin,
    Error("T", "Named type arguments are not allowed for type constructors")
  )

  def testTypeConstructorNamedTypeArgsWithoutFeatureImport(): Unit = assertErrors(
    """
      |class C[T]
      |type X = C[T = Int]
      |""".stripMargin,
    Error("T", "Named type arguments are not allowed for type constructors")
  )

  def testMethodCallNamedTypeArgNameMustResolve(): Unit = assertErrors(
    """
      |import scala.language.experimental.namedTypeArguments
      |
      |def construct[Elem](xs: Elem*): Elem = ???
      |
      |val xs = construct[Unknown = Int](1)
      |""".stripMargin,
    Error("Unknown", "Cannot resolve symbol Unknown"),
    Error("1", "Type mismatch, expected: Elem, actual: Int")
  )

  def testMethodCallDuplicateNamedTypeArg(): Unit = assertErrors(
    """
      |import scala.language.experimental.namedTypeArguments
      |
      |def construct[A, B](): Unit = ()
      |
      |construct[A = Int, A = String]()
      |""".stripMargin,
    Error("A", "Duplicate named type argument: A")
  )
}
