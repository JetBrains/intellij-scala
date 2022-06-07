package org.jetbrains.plugins.scala
package codeInspection
package hashCodeUsesVar

import com.intellij.codeInspection.LocalInspectionTool

/**
  * Daniyar Itegulov
  * 2016-02-08
  */
class HashCodeUsesVarInspectionTest extends ScalaInspectionTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[HashCodeUsesVarInspection]

  override protected val description: String =
    "Non-value field is accessed in 'hashCode()'"

  def testReturnsVar(): Unit = {
    val text = s"""class A {
                   |    var a = 1
                   |
                   |    override def hashCode(): Int = {
                   |      ${START}a$END
                   |    }
                   |}""".stripMargin
    checkTextHasError(text)
  }

  def testReturnsVal(): Unit = {
    val text = s"""class A {
                   |    val a = 1
                   |
                   |    override def hashCode(): Int = {
                   |      ${START}a$END
                   |    }
                   |}""".stripMargin
    checkTextHasNoErrors(text)
  }

  def testDefineValThroughVar(): Unit = {
    val text = s"""class A {
                   |    var a = 1
                   |
                   |    override def hashCode(): Int = {
                   |      val c = ${START}a$END
                   |      c
                   |    }
                   |}""".stripMargin
    checkTextHasError(text)
  }

  def testUseVarFromAncestor(): Unit = {
    val text = s"""class A {
                   |    var a = 1
                   |}
                   |class B extends A {
                   |    override def hashCode(): Int = {
                   |      ${START}a$END
                   |    }
                   |}""".stripMargin
    checkTextHasError(text)
  }

  def testUseVarTuple(): Unit = {
    val text = s"""class A {
                   |    var a = (1, 2)
                   |
                   |    override def hashCode(): Int = {
                   |      ${START}a$END._1
                   |    }
                   |}""".stripMargin
    checkTextHasError(text)
  }

  def testUseVarOperations(): Unit = {
    val text = s"""class A {
                   |    var a = 1
                   |
                   |    override def hashCode(): Int = {
                   |      (7 + 14 * ${START}a$END) / 21
                   |    }
                   |}""".stripMargin
    checkTextHasError(text)
  }

  def testUseVarInNonHashCode(): Unit = {
    val text = s"""class A {
                   |    var a = 1
                   |
                   |    override def hashCode(): Unit = {
                   |      ${START}a$END
                   |    }
                   |}""".stripMargin
    checkTextHasNoErrors(text)
  }

  def testUseVarInInnerFun(): Unit = {
    val text = s"""class A {
                   |    var a = 1
                   |
                   |    override def hashCode(): Int = {
                   |      def f(): Int = ${START}a$END
                   |      f()
                   |    }
                   |}""".stripMargin
    checkTextHasError(text)
  }

  def testUseVarInInnerClass(): Unit = {
    val text = s"""class A {
                   |    var a = 1
                   |
                   |    override def hashCode(): Int = {
                   |      class B {
                   |        def f(): Int = ${START}a$END
                   |      }
                   |      new B().f()
                   |    }
                   |}""".stripMargin
    checkTextHasError(text)
  }

  def testUseVarAsAcuumulator(): Unit = {
    val text = s"""class A {
                   |    val a = 1
                   |    val b = 1
                   |
                   |    override def hashCode(): Int = {
                   |      var r = 0
                   |      r += a
                   |      r += b
                   |      r
                   |    }
                   |}""".stripMargin
    checkTextHasNoErrors(text)
  }
}
