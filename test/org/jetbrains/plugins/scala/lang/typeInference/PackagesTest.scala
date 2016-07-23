package org.jetbrains.plugins.scala
package lang
package typeInference

import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression

/**
  * @author Alefas
  * @since 22/03/16
  */
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
}