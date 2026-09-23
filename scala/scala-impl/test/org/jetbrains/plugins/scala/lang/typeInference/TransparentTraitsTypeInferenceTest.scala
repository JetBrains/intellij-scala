package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class TransparentTraitsTypeInferenceTest extends TypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_3
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

  def testTransparentAnnot(): Unit = doTest {
    s"""
       |@scala.annotation.transparentTrait trait S
       |trait Kind
       |object Var extends Kind, S
       |object Val extends Kind, S
       |val x = ${START}Set(if true then Val else Var)$END
       |//Set[Kind]
       |""".stripMargin
  }

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
