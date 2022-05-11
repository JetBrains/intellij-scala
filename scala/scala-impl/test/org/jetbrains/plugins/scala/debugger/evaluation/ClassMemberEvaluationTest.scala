package org.jetbrains.plugins.scala
package debugger
package evaluation

import org.junit.experimental.categories.Category

@Category(Array(classOf[DebuggerTests]))
class ClassMemberEvaluationTest_2_11 extends ClassMemberEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_11
}

@Category(Array(classOf[DebuggerTests]))
class ClassMemberEvaluationTest_2_12 extends ClassMemberEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12
}

@Category(Array(classOf[DebuggerTests]))
class ClassMemberEvaluationTest_2_13 extends ClassMemberEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13
}

@Category(Array(classOf[DebuggerTests]))
class ClassMemberEvaluationTest_3_0 extends ClassMemberEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_0
}

@Category(Array(classOf[DebuggerTests]))
class ClassMemberEvaluationTest_3_1 extends ClassMemberEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_1
}

abstract class ClassMemberEvaluationTestBase extends ExpressionEvaluationTestBase {
  addSourceFile("Nested.scala",
    s"""object Nested {
       |  val inNested: Int = 1
       |
       |  class Outer {
       |    val inOuter: Int = 2
       |
       |    class Inner {
       |      val inInner: Int = 3
       |
       |      def foo(): Unit = {
       |        println(inOuter) $breakpoint
       |      }
       |    }
       |
       |    def foo(): Unit = {
       |      new Inner().foo()
       |    }
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    new Outer().foo()
       |  }
       |}
       |""".stripMargin.trim
  )

  def testNested(): Unit = expressionEvaluationTest() { implicit ctx =>
    "inNested" evaluatesTo 1
    "inOuter" evaluatesTo 2
    "inInner" evaluatesTo 3
  }

  addSourceFile("NestedPrivate.scala",
    s"""object NestedPrivate {
       |  private[this] val inNested: Int = 1
       |
       |  class Outer {
       |    private val inOuter: Int = 2
       |
       |    class Inner {
       |      private[this] val inInner: Int = 3
       |
       |      def foo(): Unit = {
       |        println(inOuter) $breakpoint
       |        println(inInner)
       |      }
       |    }
       |
       |    def foo(): Unit = {
       |      new Inner().foo()
       |    }
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    new Outer().foo()
       |    println(inNested)
       |  }
       |}
       |""".stripMargin.trim
  )

  def testNestedPrivate(): Unit = expressionEvaluationTest() { implicit ctx =>
    "inNested" evaluatesTo 1
    "inOuter" evaluatesTo 2
    "inInner" evaluatesTo 3
  }

  addSourceFile("MyTrait.scala",
    s"""trait MyTrait {
       |  def foo(): Unit
       |}
       |""".stripMargin.trim
  )

  addSourceFile("AnonymousClass.scala",
    s"""object AnonymousClass {
       |  val outer: Int = 1
       |
       |  def main(args: Array[String]): Unit = {
       |    val anonymous = new MyTrait {
       |      override def foo(): Unit =
       |        println() $breakpoint
       |    }
       |
       |    anonymous.foo()
       |  }
       |}
       |""".stripMargin.trim
  )

  def testAnonymousClass(): Unit = expressionEvaluationTest() { implicit ctx =>
    "outer" evaluatesTo 1
  }

  addSourceFile("AnonymousClassPrivate.scala",
    s"""object AnonymousClassPrivate {
       |  private[this] val outer: Int = 1
       |
       |  def main(args: Array[String]): Unit = {
       |    val anonymous = new MyTrait {
       |      override def foo(): Unit =
       |        println(outer) $breakpoint
       |    }
       |
       |    anonymous.foo()
       |  }
       |}
       |""".stripMargin.trim
  )

  def testAnonymousClassPrivate(): Unit = expressionEvaluationTest() { implicit ctx =>
    "outer" evaluatesTo 1
  }
}
