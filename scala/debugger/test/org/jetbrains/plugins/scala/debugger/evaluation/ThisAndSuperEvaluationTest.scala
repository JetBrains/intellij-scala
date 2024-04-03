package org.jetbrains.plugins.scala
package debugger
package evaluation

class ThisAndSuperEvaluationTest_2_11 extends ThisAndSuperEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11
}

class ThisAndSuperEvaluationTest_2_12 extends ThisAndSuperEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12
}

class ThisAndSuperEvaluationTest_2_13 extends ThisAndSuperEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

class ThisAndSuperEvaluationTest_3 extends ThisAndSuperEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3
}

class ThisAndSuperEvaluationTest_3_RC extends ThisAndSuperEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_RC
}

abstract class ThisAndSuperEvaluationTestBase extends ExpressionEvaluationTestBase {
  addSourceFile("TraitThis.scala",
    s"""
       |object TraitThis {
       |  trait Z {
       |    def foo = {
       |      println() $breakpoint
       |    }
       |  }
       |  def main(args: Array[String]): Unit = {
       |    new Z {}.foo
       |  }
       |}
      """.stripMargin.trim
  )

  def testTraitThis(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalStartsWith("this", "TraitThis$$anon")
    }
  }

  addSourceFile("Base.scala",
    s"""
       |class BaseClass {
       |  def foo = 1
       |}
       |
       |trait BaseTrait {
       |  def foo = 1
       |}
      """.stripMargin.trim
  )
  addSourceFile("SuperInvocation.scala",
    s"""
       |object SuperInvocation extends BaseClass {
       |  def main(args: Array[String]): Unit = {
       |    println() $breakpoint
       |  }
       |}
      """.stripMargin.trim
  )

  def testSuperInvocation(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("foo", "1")
    }
  }

  addSourceFile("InvocationFromInner.scala",
    s"""
       |object InvocationFromInner extends BaseClass {
       |  trait Z {
       |    def goo = {
       |      println() $breakpoint
       |    }
       |  }
       |  def main(args: Array[String]): Unit = {
       |    new Z {}.goo
       |  }
       |}
      """.stripMargin.trim
  )

  def testInvocationFromInner(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("foo", "1")
    }
  }

  addSourceFile("ThisInvocationFromInner.scala",
    s"""
       |object ThisInvocationFromInner extends BaseClass {
       |  trait Z {
       |    def foo = {
       |      println() $breakpoint
       |    }
       |  }
       |  def main(args: Array[String]): Unit = {
       |    new Z {}.foo
       |  }
       |}
      """.stripMargin.trim
  )

  def testThisInvocationFromInner(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("ThisInvocationFromInner.this.foo", "1")
    }
  }

  addSourceFile("ThisInvocationFromInnerClass.scala",
    s"""
       |class ThisInvocationFromInnerClass extends BaseClass {
       |  trait Z {
       |    def foo = {
       |      println() $breakpoint
       |    }
       |  }
       |  def boo(args: Array[String]) = {
       |    new Z {}.foo
       |  }
       |}
       |object ThisInvocationFromInnerClass {
       |  def main(args: Array[String]): Unit = {
       |    val sample = new ThisInvocationFromInnerClass
       |    sample.boo(args)
       |  }
       |}
      """.stripMargin.trim
  )

  def testThisInvocationFromInnerClass(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("ThisInvocationFromInnerClass.this.foo", "1")
    }
  }

  addSourceFile("SuperInvocationFromInner.scala",
    s"""
       |object SuperInvocationFromInner extends BaseClass {
       |  trait Z {
       |    def foo = {
       |      println() $breakpoint
       |    }
       |  }
       |  def main(args: Array[String]): Unit = {
       |    new Z {}.foo
       |  }
       |}
      """.stripMargin.trim
  )

  def testSuperInvocationFromInner(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("SuperInvocationFromInner.super.foo", "1")
    }
  }

  addSourceFile("SuperTraitInvocationFromInner.scala",
    s"""
       |class SuperTraitInvocationFromInner extends BaseTrait {
       |  trait Z {
       |    def foo = {
       |      println() $breakpoint
       |    }
       |  }
       |  def boo(args: Array[String]) = {
       |    new Z {}.foo
       |  }
       |}
       |object SuperTraitInvocationFromInner {
       |  def main(args: Array[String]) = {
       |    new SuperTraitInvocationFromInner().boo(args)
       |  }
       |}
      """.stripMargin.trim
  )

  def testSuperTraitInvocationFromInner(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("SuperTraitInvocationFromInner.super.foo", "1")
    }
  }

  addSourceFile("SuperTraitInvocation.scala",
    s"""
       |object SuperTraitInvocation extends BaseTrait {
       |  def main(args: Array[String]): Unit = {
       |    println() $breakpoint
       |  }
       |}
      """.stripMargin.trim
  )

  def testSuperTraitInvocation(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("foo", "1")
    }
  }

  addSourceFile("Sample.scala",
    s"""
       |trait IOI {
       |  def ioi = 2
       |}
       |trait E extends IOI {
       |  trait FF {
       |    def ioi = 1
       |  }
       |
       |  trait F extends FF {
       |    def foo = {
       |      E.super.ioi
       |      println() $breakpoint
       |    }
       |  }
       |  def moo = {new F{}.foo}
       |}
       |object OuterSuperInnerTraitInvocation {
       |  def main(args: Array[String]): Unit = {
       |    new E {}.moo
       |  }
       |}
      """.stripMargin.trim
  )

  def testOuterSuperInnerTraitInvocation(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("E.super.ioi", "2")
    }
  }

  addSourceFile("InnerOuterEtc.scala",
    s"""
       |object InnerOuterEtc {
       |  class Outer extends BaseClass {
       |    trait Z {
       |      def goo = {
       |        println() $breakpoint
       |      }
       |    }
       |
       |    def goo = {
       |      new Z {}.goo
       |    }
       |  }
       |  def main(args: Array[String]): Unit = {
       |    new Outer().goo
       |  }
       |}
      """.stripMargin.trim
  )

  def testInnerOuterEtc(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("foo", "1")
    }
  }

  addSourceFile("InnerOuterInheritedOuterFieldEtc.scala",
    s"""
       |object InnerOuterInheritedOuterFieldEtc {
       |  class Outer extends BaseClass {
       |    class HasOuterField {
       |      def capture = Outer.this
       |    }
       |    class Z extends HasOuterField {
       |      def goo = {
       |        println() $breakpoint
       |      }
       |    }
       |
       |    def goo = {
       |      new Z {}.goo
       |    }
       |  }
       |  def main(args: Array[String]): Unit = {
       |    new Outer().goo
       |  }
       |}
      """.stripMargin.trim
  )

  def testInnerOuterInheritedOuterFieldEtc(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("foo", "1")
    }
  }
}
