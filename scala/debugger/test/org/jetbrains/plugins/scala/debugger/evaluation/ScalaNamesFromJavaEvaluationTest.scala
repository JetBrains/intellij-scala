//package org.jetbrains.plugins.scala.debugger.evaluation

// TODO Reinstate when SCL-21276 is resolved.
//  See https://youtrack.jetbrains.com/issue/SCL-21276/Java-debugger-expression-evaluator-cant-resolve-names-with-backticks#focus=Comments-27-7434602.0-0

//import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
//import org.junit.runner.RunWith
//
//@RunWith(classOf[MultipleScalaVersionsRunner])
//@RunWithScalaVersions(Array(
//  TestScalaVersion.Scala_2_11,
//  TestScalaVersion.Scala_2_12,
//  TestScalaVersion.Scala_2_13,
//  TestScalaVersion.Scala_3_Latest
//))
//class ScalaNamesFromJavaEvaluationTest extends ExpressionEvaluationTestBase {
//
//  addJavaSourceFile("JavaMain.java", "JavaMain",
//    s"""public class JavaMain {
//       |    public static void main(String[] args) {
//       |        System.out.println(); $breakpoint
//       |    }
//       |}""".stripMargin.trim)
//
//  addSourceFile("backticks.scala",
//    s"""class `Foo`
//       |class `A?`
//       |""".stripMargin)
//
//
//  def testBackticks(): Unit = expressionEvaluationTest("JavaMain") { implicit ctx =>
//    evalEquals("new Foo() == new A$qmark()", "false")
//  }
//}
