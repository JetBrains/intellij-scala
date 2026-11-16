package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

abstract class TransparentTraitsTypeInferenceTestBase extends TypeInferenceTestBase {
  // Scala 3.8 deprecated `@transparentTrait`, and the compiler no longer takes it into account
  private def isSince3_8: Boolean = version >= LatestScalaVersions.Scala_3_8

  private def byVersion(before3_8: String, since3_8: String): String =
    if (isSince3_8) since3_8 else before3_8

  private val annotatedS =
    """@scala.annotation.transparentTrait trait S
      |trait Kind
      |object Var extends Kind, S
      |object Val extends Kind, S
      |object Other extends Kind
      |""".stripMargin

  def testTransparentKW(): Unit = doTest {
    s"""
       |transparent trait S
       |trait Kind
       |object Var extends Kind, S
       |object Val extends Kind, S
       |val x = ${START}Set(if true then Val else Var)$END
       |//Set[Kind]
       |""".stripMargin
  }

  def testTransparentKW2(): Unit = doTest {
    s"""
       |transparent trait Kind
       |object Var extends Kind
       |object Val extends Kind
       |val x = ${START}Set(if true then Val else Var)$END
       |//Set[Val.type | Var.type]
       |""".stripMargin
  }

  def testTransparentKWErrors(): Unit = assertNoErrors(
    """transparent trait S
      |trait Kind
      |object Var extends Kind, S
      |object Val extends Kind, S
      |object Other extends Kind
      |
      |val x = Set(if true then Val else Var)
      |val y: Set[Kind] = x
      |val z = x + Other
      |""".stripMargin
  )

  def testTransparentAnnot(): Unit = doTest {
    s"""
       |$annotatedS
       |val x = ${START}Set(if true then Val else Var)$END
       |//${byVersion("Set[Kind]", "Set[Kind & S]")}
       |""".stripMargin
  }

  def testTransparentAnnotVarargs(): Unit = doTest {
    s"""
       |$annotatedS
       |val x = ${START}List(Val, Var)$END
       |//${byVersion("List[Kind]", "List[Kind & S]")}
       |""".stripMargin
  }

  def testTransparentAnnotVal(): Unit = doTest {
    s"""
       |$annotatedS
       |val x = if true then Val else Var
       |${START}x$END
       |//${byVersion("Kind", "Kind & S")}
       |""".stripMargin
  }

  def testTransparentAnnotDef(): Unit = doTest {
    s"""
       |$annotatedS
       |def x = if true then Val else Var
       |${START}x$END
       |//${byVersion("Kind", "Kind & S")}
       |""".stripMargin
  }

  def testTransparentAnnotFunctionLiteral(): Unit = doTest {
    s"""
       |$annotatedS
       |val x = (b: Boolean) => if b then Val else Var
       |${START}x$END
       |//${byVersion("Boolean => Kind", "Boolean => Kind & S")}
       |""".stripMargin
  }

  def testTransparentAnnotErrors(): Unit = {
    val code =
      s"""$annotatedS
         |val x = Set(if true then Val else Var)
         |val y: Set[Kind] = x
         |val z = x + Other
         |""".stripMargin

    if (isSince3_8)
      assertErrorsText(
        code,
        """Error(x,Expression of type Set[Kind & S] doesn't conform to expected type Set[Kind])
          |Error(Other,Type mismatch, expected: Kind & S, actual: Other.type)""".stripMargin
      )
    else
      assertNoErrors(code)
  }

  // `Product` and `Serializable`
  def testCaseClasses(): Unit = doTest {
    s"""
       |case class C1(i: Int)
       |case class C2(i: Int)
       |val x = if true then C1(1) else C2(1)
       |${START}x$END
       |//C1 | C2
       |""".stripMargin
  }

  // Before 3.8, the compiler assumes the collection traits of the 2.13 standard library to be
  // transparent, since 3.8 the standard library declares them `transparent`
  private def doCollectionTest(lhs: String, rhs: String, expected: String): Unit = doTest {
    s"""
       |import scala.collection.{immutable, mutable}
       |val x = if true then $lhs else $rhs
       |${START}x$END
       |//$expected
       |""".stripMargin
  }

  def testCollectionSeqOps(): Unit =
    doCollectionTest("List(1)", "Vector(1)", "AbstractSeq[Int]")

  def testCollectionIndexedSeqOps(): Unit =
    doCollectionTest("Vector(1)", "immutable.ArraySeq(1)", "AbstractSeq[Int] & IndexedSeq[Int]")

  def testCollectionSortedMapOps(): Unit =
    doCollectionTest("immutable.TreeMap(1 -> 1)", "mutable.TreeMap(1 -> 1)", "AbstractMap[Int, Int] & SortedMap[Int, Int]")

  def testCollectionSortedSetOps(): Unit =
    doCollectionTest("immutable.BitSet(1)", "mutable.BitSet(1)", "AbstractSet[Int] & BitSet")

  def testBuiltinTransparent(): Unit = doTest {
    s"""
       |object A {
       |  sealed trait Item
       |  case class ItemA(x: Double) extends Item
       |  case class ItemB(x: Double) extends Item
       |
       |  val set = ${START}Set(ItemA(0), ItemB(1))$END
       |}
       |//Set[A.Item]
       |""".stripMargin
  }
}

class TransparentTraitsTypeInferenceTest_Scala_3_3 extends TransparentTraitsTypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_3
}

class TransparentTraitsTypeInferenceTest_Scala_3_7 extends TransparentTraitsTypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_7
}

class TransparentTraitsTypeInferenceTest_Scala_3_8 extends TransparentTraitsTypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_8
}

class TransparentTraitsTypeInferenceTest_Scala_3_10 extends TransparentTraitsTypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_10
}
