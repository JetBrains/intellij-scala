package org.jetbrains.plugins.scala.debugger.breakpoints

import org.jetbrains.plugins.scala.ScalaVersion

class TryCatchInLazyValTest_2_11 extends TryCatchInLazyValTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11

  override def testTryCatchInLazyVal(): Unit = {
    breakpointsTest()(
      (2, "simpleName$lzycompute"), (4, "liftedTree1$1"), (7, "liftedTree1$1"), (8, "liftedTree1$1")
    )
  }

  override def testTryCatchInLocalLazyValInLazyVal(): Unit = {
    breakpointsTest()(
      (14, "simpleName$lzycompute"), (3, "inner$lzycompute$1"), (5, "liftedTree1$1"), (8, "liftedTree1$1"), (9, "liftedTree1$1")
    )
  }
}


class TryCatchInLazyValTest_2_12 extends TryCatchInLazyValTest_2_11 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12
}


class TryCatchInLazyValTest_2_13 extends TryCatchInLazyValTest_2_11 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

class TryCatchInLazyValTest_3 extends TryCatchInLazyValTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override def testTryCatchInLazyVal(): Unit = {
    breakpointsTest()(
      (2, "simpleName$lzyINIT1"), (4, "simpleName$lzyINIT1"), (7, "simpleName$lzyINIT1"), (8, "simpleName$lzyINIT1")
    )
  }

  override def testTryCatchInLocalLazyValInLazyVal(): Unit = {
    breakpointsTest()(
      (14, "simpleName$lzyINIT1"), (3, "inner$lzyINIT1$1"), (5, "inner$lzyINIT1$1"), (8, "inner$lzyINIT1$1"), (9, "inner$lzyINIT1$1")
    )
  }
}

class TryCatchInLazyValTest_3_4 extends TryCatchInLazyValTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_4
}

class TryCatchInLazyValTest_3_5 extends TryCatchInLazyValTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_5
}

class TryCatchInLazyValTest_3_6 extends TryCatchInLazyValTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_6
}

class TryCatchInLazyValTest_3_7 extends TryCatchInLazyValTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_7
}

class TryCatchInLazyValTest_3_LTS_RC extends TryCatchInLazyValTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_LTS_RC
}

class TryCatchInLazyValTest_3_Next_RC extends TryCatchInLazyValTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_Next_RC
}

abstract class TryCatchInLazyValTestBase extends BreakpointsTestBase {

  addSourceFile("TryCatchInLazyVal.scala",
    s"""object TryCatchInLazyVal {
       |  lazy val simpleName: String = {
       |    val res = "abc" $breakpoint
       |    try {
       |      Integer.parseInt("res") $breakpoint
       |    } catch {
       |      case e: NumberFormatException => {
       |        print("test") $breakpoint
       |        throw e; $breakpoint
       |      }
       |    }
       |    res
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    simpleName
       |  }
       |}
       |""".stripMargin)

  def testTryCatchInLazyVal(): Unit

  addSourceFile("TryCatchInLocalLazyValInLazyVal.scala",
    s"""object TryCatchInLocalLazyValInLazyVal {
       |  lazy val simpleName: String = {
       |    lazy val inner = {
       |      val res = "abc" $breakpoint
       |      try {
       |        Integer.parseInt("res") $breakpoint
       |      } catch {
       |        case e: NumberFormatException => {
       |          print("test") $breakpoint
       |          throw e; $breakpoint
       |        }
       |      }
       |      res
       |    }
       |    inner $breakpoint
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    simpleName
       |  }
       |}
       |""".stripMargin)

  def testTryCatchInLocalLazyValInLazyVal(): Unit
}
