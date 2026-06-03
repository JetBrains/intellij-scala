package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

class AdjustTypesIntentionTest extends ScalaIntentionTestBase {

  override def familyName: String = "Adjust types"

  def test_example(): Unit = doTest(
    text =
      s"""
         |val test: sc${CARET}ala.collection.mutable.Seq[String] = ???
         |""".stripMargin,
    resultText =
      s"""import scala.collection.mutable
         |
         |val test: mutable.Seq[String] = ???
         |""".stripMargin
  )

  def test_example_2(): Unit = doTest(
    text =
      s"""
         |val test: scala.collection.mutable.S${CARET}eq[String] = ???
         |""".stripMargin,
    resultText =
      s"""import scala.collection.mutable
         |
         |val test: mutable.Seq[String] = ???
         |""".stripMargin
  )

  def test_example_3(): Unit = doTest(
    text =
      s"""
         |val test: scala.collect${CARET}ion.mutable.Seq[String] = ???
         |""".stripMargin,
    resultText =
      s"""import scala.collection.mutable
         |
         |val test: mutable.Seq[String] = ???
         |""".stripMargin
  )

  def test_insert_import_into_existing_well_organized(): Unit = doTest(
    s"""import java.util.ArrayList
       |import scala.collection.immutable.Set
       |import scala.languageFeature._
       |import scala.util.{Properties => Properties_Unused, Random => Random_Ranamed}
       |
       |class A {
       |  val test1: scala.collection.${CARET}mutable.Seq[String] = ???
       |
       |  val x: Random_Ranamed = ???
       |  val set: Set[_] = ???
       |}""".stripMargin,
    s"""import java.util.ArrayList
       |import scala.collection.immutable.Set
       |import scala.collection.mutable
       |import scala.languageFeature._
       |import scala.util.{Properties => Properties_Unused, Random => Random_Ranamed}
       |
       |class A {
       |  val test1: mutable.Seq[String] = ???
       |
       |  val x: Random_Ranamed = ???
       |  val set: Set[_] = ???
       |}""".stripMargin
  )

  def test_insert_import_into_existing_badly_organized(): Unit = doTest(
    s"""import scala.util.{Random => Random_Ranamed, Properties => Properties_Unused, _}
       |
       |import scala.collection.immutable.Set
       |
       |import java.util.ArrayList
       |
       |import scala.languageFeature._
       |
       |class A {
       |  val test1: scala.collection.mutable.${CARET}Seq[String] = ???
       |
       |  val x: Random_Ranamed = ???
       |  val set: Set[_] = ???
       |}
       |""".stripMargin,
    s"""import scala.util.{Properties => Properties_Unused, Random => Random_Ranamed, _}
       |import scala.collection.immutable.Set
       |import java.util.ArrayList
       |import scala.collection.mutable
       |import scala.languageFeature._
       |
       |class A {
       |  val test1: mutable.Seq[String] = ???
       |
       |  val x: Random_Ranamed = ???
       |  val set: Set[_] = ???
       |}
       |""".stripMargin
  )


  def test_use_type_alias(): Unit = doTest(
    s"""
       |class A {
       |  type MyAlias = Int
       |  def bar: ${CARET}Int = 1
       |}
       |""".stripMargin,
    s"""
       |class A {
       |  type MyAlias = Int
       |
       |  def bar: MyAlias = 1
       |}
       |""".stripMargin,
  )
}
