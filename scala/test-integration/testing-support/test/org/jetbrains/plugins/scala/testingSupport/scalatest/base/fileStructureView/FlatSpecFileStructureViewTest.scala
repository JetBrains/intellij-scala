package org.jetbrains.plugins.scala.testingSupport.scalatest.base.fileStructureView

import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.structureView.element.{Test, TypeDefinition}
import org.jetbrains.plugins.scala.structureView.element.Test._
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestConfigurationProducer
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider
import org.junit.Assert.assertEquals

import scala.jdk.CollectionConverters._

trait FlatSpecFileStructureViewTest extends ScalaTestTestCase {

  private val className = "FlatSpecViewTest"

  addSourceFile(className + ".scala",
    s"""$ImportsForFlatSpec
       |
       |class $className extends $FlatSpecBase {
       |  behavior of "first"
       |
       |  it should "child1" in {}
       |
       |  ignore should "ignore1" in {}
       |
       |  "second" should "pend1" in pending
       |
       |  it should "pend2" is pending
       |
       |  they should "child2" in {}
       |
       |  they should "ignore2" ignore {}
       |
       |  ignore should "ignore and pend" is pending
       |
       |  it should "ignore and pend2" ignore pending
       |}
       |""".stripMargin)

  def testFlatSpecNormal(): Unit =
    runFileStructureViewTest(className, NormalStatusId, "behavior of \"first\"", "it should \"child1\"", "they should \"child2\"")

  // SCL-25924: based on the ShoppingCart/PaymentProcessor example attached by QA.
  private val behaviorOfClassNameInfix = "BehaviorOfStructureSpec"
  private val behaviorOfSourceInfix =
    s"""$ImportsForFlatSpec
       |
       |class $behaviorOfClassNameInfix extends $FlatSpecBase {
       |  behavior of "ShoppingCart"
       |  it should "start empty" in {}
       |  it should "add an item" in {}
       |  it should "remove an item" in {}
       |
       |  behavior of "PaymentProcessor"
       |  it should "reject a zero amount" in {}
       |  they should "accept a positive amount" in {}
       |
       |  object other {
       |    def of(subject: String): Unit = ()
       |  }
       |  other of "Not a ScalaTest subject"
       |}
       |""".stripMargin

  addSourceFile(behaviorOfClassNameInfix + ".scala", behaviorOfSourceInfix)

  private val behaviorOfClassNameMethodCall = "BehaviorOfMethodCallStructureSpec"
  private val behaviorOfSourceMethodCall =
    s"""$ImportsForFlatSpec
       |
       |class $behaviorOfClassNameMethodCall extends $FlatSpecBase {
       |  behavior.of("ShoppingCart")
       |  it should "start empty" in {}
       |  it should "add an item" in {}
       |  it should "remove an item" in {}
       |
       |  behavior.of("PaymentProcessor")
       |  it should "reject a zero amount" in {}
       |  they should "accept a positive amount" in {}
       |
       |  object other {
       |    def of(subject: String): Unit = ()
       |  }
       |  other.of("Not a ScalaTest subject")
       |}
       |""".stripMargin

  addSourceFile(behaviorOfClassNameMethodCall + ".scala", behaviorOfSourceMethodCall)

  def testFlatSpecBehaviorOfOrder(): Unit =
    assertBehaviorOfStructure(behaviorOfClassNameInfix, "behavior of \"ShoppingCart\"", "behavior of \"PaymentProcessor\"")

  def testFlatSpecBehaviorOfMethodCallOrder(): Unit =
    assertBehaviorOfStructure(behaviorOfClassNameMethodCall, "behavior.of(\"ShoppingCart\")", "behavior.of(\"PaymentProcessor\")")

  private def assertBehaviorOfStructure(testClassName: String, firstSubject: String, secondSubject: String): Unit =
    runFileStructureViewTest0(testClassName, root => inReadAction {
      val classes = root.getChildren.asScala.toSeq
      assertEquals("Expected one test suite", 1, classes.size)
      val suiteNode = classes.head
      val tests = suiteNode.getChildren.asScala.toSeq.map(_.getValue).collect { case test: Test => test }

      // Compare the actual model order, without sorting away misplaced or duplicate subject entries.
      assertEquals(Seq(
        firstSubject,
        "it should \"start empty\"",
        "it should \"add an item\"",
        "it should \"remove an item\"",
        secondSubject,
        "it should \"reject a zero amount\"",
        "they should \"accept a positive amount\"",
      ), tests.map(_.getPresentableText))

      // The node provider also supplies regexp run configurations: subjects must not become test names.
      val suite = suiteNode.getValue.asInstanceOf[TypeDefinition].element
      val testNames = TestNodeProvider.getTestNames(suite, ScalaTestConfigurationProducer())
      assertEquals(Seq(
        "ShoppingCart should start empty",
        "ShoppingCart should add an item",
        "ShoppingCart should remove an item",
        "PaymentProcessor should reject a zero amount",
        "PaymentProcessor should accept a positive amount",
      ).sorted, testNames.sorted)
    })

  def testFlatSpecIgnored(): Unit =
    runFileStructureViewTest(className, IgnoredStatusId, "ignore should \"ignore1\"", "they should \"ignore2\"")

  def testFlatSpecIgnoredAndPending(): Unit =
    runFileStructureViewTest(className, IgnoredStatusId, "ignore should \"ignore and pend\"", "it should \"ignore and pend2\"")

  def testFlatSpecPending(): Unit =
    runFileStructureViewTest(className, PendingStatusId, "\"second\" should \"pend1\"", "it should \"pend2\"")
}
