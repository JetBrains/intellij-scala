package org.jetbrains.plugins.scala.lang.resolve.imports


import com.intellij.psi.PsiPolyVariantReference
import java.lang.String
import util.TestUtils

/**
 * @author ilyas
 */

class ImportsUsagesTest extends ScalaResolveTestCase {
  override def getTestDataPath: String = TestUtils.getTestDataPath() + "/resolve/imports/"

  def printResults(imports: ScalaObject) = {
    println("[" + getTestName(false) + "]")
    println("------------------------------------------------")
    println(imports)
    println
  }

  def testSimpleImport(): Unit = {
    val path = "simple/SimpleImport.scala"
    configureByFile(path) match {
      case r: PsiPolyVariantReference => {
        val results = r.multiResolve(false)

        assert(results.length == 1)
        val res = results(0)
        assert(res.isInstanceOf[ScalaResolveResult])
        val srr = res.asInstanceOf[ScalaResolveResult]

        assert(srr.getElement != null)
        val imports = srr.importsUsed
        assert(imports.size == 3)

        printResults(imports)
      }
      case _ => throw new Exception("Wrong reference!")
    }
  }


  def testImportSelector(): Unit = {
    val path = "selector/ImportSelector.scala"
    configureByFile(path) match {
      case r: PsiPolyVariantReference => {
        val results = r.multiResolve(false)

        assert(results.length == 1)
        val res = results(0)
        assert(res.isInstanceOf[ScalaResolveResult])
        val srr = res.asInstanceOf[ScalaResolveResult]

        assert(srr.getElement != null)
        val imports = srr.importsUsed
        assert(imports.size == 3)

        printResults(imports)

      }
      case _ => throw new Exception("Wrong reference!")
    }
  }

  def testShadowing1(): Unit = {
    val path = "shadow1/Shadow.scala"
    configureByFile(path) match {
      case r: PsiPolyVariantReference => {
        val results = r.multiResolve(false)

        assert(results.length == 1)
        val res = results(0)
        assert(res.isInstanceOf[ScalaResolveResult])
        val srr = res.asInstanceOf[ScalaResolveResult]

        assert(srr.getElement != null)
        val imports = srr.importsUsed
        assert(imports.size == 1)

        printResults(imports)

      }
      case _ => throw new Exception("Wrong reference!")
    }
  }

  def testShadowing2(): Unit = {
    val path = "shadow2/Shadow.scala"
    configureByFile(path) match {
      case r: PsiPolyVariantReference => {
        val results = r.multiResolve(false)

        assert(results.length == 1)
        val res = results(0)
        assert(res.isInstanceOf[ScalaResolveResult])
        val srr = res.asInstanceOf[ScalaResolveResult]

        assert(srr.getElement != null)
        val imports = srr.importsUsed
        assert(imports.size == 2)

        printResults(imports)

      }
      case _ => throw new Exception("Wrong reference!")
    }
  }

  def testImplicits(): Unit = {
    val path = "implicit/implicits.scala"
    configureByFile(path) match {
      case r: PsiPolyVariantReference => {
        val results = r.multiResolve(false)

        assert(results.length == 1)
        val res = results(0)
        assert(res.isInstanceOf[ScalaResolveResult])
        val srr = res.asInstanceOf[ScalaResolveResult]

        assert(srr.getElement != null)
        val imports = srr.importsUsed
        assert(imports.size == 2)

        printResults(imports)

      }
      case _ => throw new Exception("Wrong reference!")
    }
  }



}