package org.jetbrains.plugins.scala
package lang
package resolve
package imports


import com.intellij.psi.PsiPolyVariantReference
import java.lang.String
import util.TestUtils

class HideImportsTest extends ScalaResolveTestCase {
  override def getTestDataPath: String = TestUtils.getTestDataPath() + "/resolve/imports/"

  def printResults(imports: ScalaObject) = {
    println("[" + getTestName(false) + "]")
    println("------------------------------------------------")
    println(imports)
    println
  }

  def testHidePredefImplicit(): Unit = {
    val path = "implicit/HidePredefImplicit.scala"
    configureByFile(path) match {
      case r: PsiPolyVariantReference => {
        val results = r.multiResolve(false)

        assert(results.length == 1)
        val res = results(0)
        assert(res.isInstanceOf[ScalaResolveResult])
        val srr = res.asInstanceOf[ScalaResolveResult]
        org.junit.Assert.assertEquals("def capitalize: Int", srr.element.getText)
      }
      case _ => throw new Exception("Wrong reference!")
    }
  }
}