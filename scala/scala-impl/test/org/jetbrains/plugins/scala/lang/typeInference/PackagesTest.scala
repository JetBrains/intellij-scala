package org.jetbrains.plugins.scala
package lang
package typeInference

import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class PackagesTest extends ScalaFixtureTestCase {
  def testSCL9540(): Unit = {
    myFixture.addFileToProject("com/example/packaje/package.scala",
      """
        |package com.example
        |
        |package object packaje {
        |  case class CaseClassWithOverloads(int: Int, string: String) {
        |    def this(combined: IntWithString) {
        |      this(combined.int, combined.string)
        |    }
        |  }
        |  object CaseClassWithOverloads {
        |    def apply(combined: IntWithString): CaseClassWithOverloads = {
        |      new CaseClassWithOverloads(combined)
        |    }
        |  }
        |
        |  case class IntWithString(int: Int, string: String)
        |}
      """.stripMargin)
    val fileToCheck = myFixture.addFileToProject("com/example/other/packaje/UseCaseClassFromPackageObject.scala",
      """
        |package com.example.other.packaje
        |
        |import com.example.packaje._
        |
        |object UseCaseClassFromPackageObject {
        |  def main(args: Array[String]) = {
        |    println(CaseClassWithOverloads(107, "a"))
        |    //println(CaseClassWithOverloads(new IntWithString(21, "b")))
        |  }
        |}
        |
      """.stripMargin)

    val visitor = new ScalaRecursiveElementVisitor {
      override def visitReferenceExpression(ref: ScReferenceExpression): Unit = {
        if (ref.refName == "CaseClassWithOverloads") {
          assert(ref.bind().nonEmpty && ref.bind().get.isApplicable(), "Resolve is not applicable")
        }
        super.visitReferenceExpression(ref)
      }
    }
    fileToCheck.accept(visitor)
  }

  def testSCL8850(): Unit = {
    myFixture.addFileToProject("tuff/scl8850/temp.txt", "Something")
    myFixture.addFileToProject("scl8850/A.scala",
      """
        |package scl8850
        |
        |object A {
        |  val a = 1
        |}
      """.stripMargin)
    val fileToCheck = myFixture.addFileToProject("tuff/MyTest.scala",
      """
        |package tuff
        |
        |import scl8850._
        |
        |class MyTest {
        |  A.a
        |}
      """.stripMargin)

    val visitor = new ScalaRecursiveElementVisitor {
      override def visitReferenceExpression(ref: ScReferenceExpression): Unit = {
        assert(ref.resolve() != null, s"Can't resolve reference ${ref.refName}")
      }
    }
    fileToCheck.accept(visitor)
  }

}