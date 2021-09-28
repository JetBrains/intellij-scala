package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.Argument.PassByValue

class ApplyAndUpdateInfoTest extends InvocationInfoTestBase {

  def testGeneratedFactoryApplyMethods(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |object Test {
         |case class SomeStringWrapper(wrapped: String)
         |
         |def main(): String = {
         |val somethingWrapped = ${markerStart}SomeStringWrapper("Wrap me")${markerEnd}
         |somethingWrapped.wrapped
         |}
         |}
         |""".stripMargin
    }

    val expectedArgCount = 1 + 1
    val expectedProperArgsInText = List("\"Wrap me\"")
    val expectedMappedParamNames = List("wrapped")
    val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

    verifyInvokedElement(invocationInfo, "SomeStringWrapper#apply")
    verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
      expectedMappedParamNames, expectedPassingMechanisms)
    verifyThisExpression(invocationInfo, "SomeStringWrapper")
  }

  def testCustomApplyMethodsInSingletonObjects(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |object Test {
         |object SomeClass {
         |def apply(x: Int): Int = 2 * x + 3
         |}
         |
         |def main(): String = {
         |val x = ${markerStart}SomeClass(4)${markerEnd}
         |x + 2
         |}
         |}
         |""".stripMargin
    }

    val expectedArgCount = 1 + 1
    val expectedProperArgsInText = List("4")
    val expectedMappedParamNames = List("x")
    val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

    verifyInvokedElement(invocationInfo, "SomeClass#apply")
    verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
      expectedMappedParamNames, expectedPassingMechanisms)
    verifyThisExpression(invocationInfo, "SomeClass")
  }

  def testCustomApplyMethodsOnInstances(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |object Test {
         |class SomeClass(y: Int) {
         |def apply(x: Int): Int = 2 * x + y
         |}
         |
         |def main(): String = {
         |val obj = new SomeClass(33)
         |val x = ${markerStart}obj(5)${markerEnd}
         |x + 3
         |}
         |}
         |""".stripMargin
    }

    val expectedArgCount = 1 + 1
    val expectedProperArgsInText = List("5")
    val expectedMappedParamNames = List("x")
    val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

    verifyInvokedElement(invocationInfo, "SomeClass#apply")
    verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
      expectedMappedParamNames, expectedPassingMechanisms)
    verifyThisExpression(invocationInfo, "obj")
  }

  def testBuiltinFactoryApplyMethodsWithFewArgs(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |object Test {
         |
         |def main(): String = {
         |val someList = ${markerStart}List(1113, 8 * 15)${markerEnd}
         |}
         |}
         |""".stripMargin
    }

    val expectedArgCount = 1 + 2
    val expectedProperArgsInText = List("1113", "8 * 15")
    val expectedMappedParamNames = List("xs", "xs")
    val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

    verifyInvokedElement(invocationInfo, "List#apply")
    verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
      expectedMappedParamNames, expectedPassingMechanisms)
    verifyThisExpression(invocationInfo, "List")
  }

  def testBuiltinFactoryApplyMethodsWithMoreArgs(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |object Test {
         |
         |def someFunc(x: Int): Int = x + 3
         |
         |def main(): String = {
         |val someList = ${markerStart}List(1113, 8 * 15, 24, 9, 32992, 9, someFunc(33), 44, 47858, 45555, 6 - 6, 323, 44)${markerEnd}
         |}
         |}
         |""".stripMargin
    }

    val expectedArgCount = 1 + 13
    val expectedProperArgsInText = List("1113", "8 * 15", "24", "9", "32992", "9", "someFunc(33)", "44",
      "47858", "45555", "6 - 6", "323", "44")
    val expectedMappedParamNames = (1 until expectedArgCount).map(_ => "xs")
    val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

    verifyInvokedElement(invocationInfo, "List#apply")
    verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
      expectedMappedParamNames, expectedPassingMechanisms)
    verifyThisExpression(invocationInfo, "List")
  }

  def testBuiltinAccessorApplyMethods(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |object Test {
         |
         |def main(): String = {
         |val someSet = Set(13, 13, 13, 1113, 8 * 15, 24, 9, 32992, 9, 33)
         |${markerStart}someSet(120)${markerEnd}
         |}
         |}
         |""".stripMargin
    }

    val expectedArgCount = 1 + 1
    val expectedProperArgsInText = List("120")
    val expectedMappedParamNames = List("elem")
    val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

    verifyInvokedElement(invocationInfo, "GenSetLike#apply")
    verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
      expectedMappedParamNames, expectedPassingMechanisms)
    verifyThisExpression(invocationInfo, "someSet")
  }

  def testCustomUpdateMethods(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |object Test {
         |class MyMutableCollection {
         |val arr = ArrayBuffer[Int](1, 2, 3, 4, 5, 6, 7)
         |def update(position: Int, value: Int): Unit = arr.insert(position, value * 2)
         |}
         |
         |def main(): String = {
         |val collection = new MyMutableCollection
         |${markerStart}collection(4) = 12${markerEnd}
         |}
         |}
         |""".stripMargin
    }

    val expectedArgCount = 1 + 2
    val expectedProperArgsInText = List("4", "12")
    val expectedMappedParamNames = List("position", "value")
    val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

    verifyInvokedElement(invocationInfo, "MyMutableCollection#update")
    verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
      expectedMappedParamNames, expectedPassingMechanisms)
    //    verifyThisExpression(invocationInfo, "collection") TODO check why if thisExpr correctly returns None in the PSI?
  }

  def testComplexCustomUpdateMethods(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |object Test {
         |class MyMutableCollection {
         |val arr = ArrayBuffer[Int](1, 2, 3, 4, 5, 6, 7)
         |def update(position1: Int, position2: String, position3: Boolean, value: Int): Unit = {
         | if (position3) arr.insert(position1, value * 2)
         | else arr.insert(position1, value * 7
         |}
         |}
         |
         |def main(): String = {
         |val collection = new MyMutableCollection
         |${markerStart}collection(4, "dddddx", 7 <= 8 && 9 > 4 * 2) = 12${markerEnd}
         |}
         |}
         |""".stripMargin
    }

    val expectedArgCount = 1 + 4
    val expectedProperArgsInText = List("4", "\"dddddx\"", "7 <= 8 && 9 > 4 * 2", "12")
    val expectedMappedParamNames = List("position1", "position2", "position3", "value")
    val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

    verifyInvokedElement(invocationInfo, "MyMutableCollection#update")
    verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
      expectedMappedParamNames, expectedPassingMechanisms)
    //    verifyThisExpression(invocationInfo, "collection") TODO check why if thisExpr correctly returns None in the PSI?
  }

  def testBuiltinUpdateMethods(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |import scala.collection.mutable.ArrayBuffer
         |
         |object Test {
         |
         |def main(): String = {
         |val someMutableArray = ArrayBuffer(11339, 9 * 40, 9 - 4 - 4 - 4, -15)
         |${markerStart}someMutableArray(3) = 15 + 2 * 9${markerEnd}
         |}
         |}
         |""".stripMargin
    }

    val expectedArgCount = 1 + 2
    val expectedProperArgsInText = List("3", "15 + 2 * 9")
    val expectedMappedParamNames = List("idx", "elem")
    val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

    verifyInvokedElement(invocationInfo, "ResizableArray#update")
    verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
      expectedMappedParamNames, expectedPassingMechanisms)
    // TODO check if this expression also here should be an unknown value
  }
}
