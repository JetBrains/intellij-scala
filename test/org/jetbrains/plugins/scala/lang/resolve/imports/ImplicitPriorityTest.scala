package org.jetbrains.plugins.scala
package lang
package resolve
package imports

import com.intellij.psi.PsiPolyVariantReference
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, ScalaResolveTestCase}
import org.jetbrains.plugins.scala.util.TestUtils

/**
 * @author jzaugg
 */
class ImplicitPriorityTest extends ScalaResolveTestCase {
  override def getTestDataPath: String = TestUtils.getTestDataPath() + "/resolve/implicitPriority/"

  def printResults(imports: ScalaObject) = {
    println("[" + getTestName(false) + "]")
    println("------------------------------------------------")
    println(imports)
    println
  }

  def testLowPriorityImplicits() {
    val path = "lowPriorityImplicits.scala"
    configureByFile(path) match {
      case r: PsiPolyVariantReference => {
        val results = r.multiResolve(false)
        assert(results.length == 1, results.mkString(","))
      }
      case x => throw new Exception("Wrong reference!" + x)
    }
  }

  def testLowPriorityImplicits2() {
    val path = "lowPriorityImplicits2.scala"
    configureByFile(path) match {
      case r: PsiPolyVariantReference => {
        val results = r.multiResolve(false)
        assert(results.length == 1, results.mkString(","))
      }
      case x => throw new Exception("Wrong reference!" + x)
    }
  }

  /*def testMostSpecificImplicit() {
    val path = "mostSpecificImplicit.scala"
    configureByFile(path) match {
      case r: PsiPolyVariantReference => {
        val results = r.multiResolve(false)
        assert(results.length == 1, results.mkString(","))
      }
      case x => throw new Exception("Wrong reference!" + x)
    }
  }*/
}