package org.jetbrains.plugins.scala
package lang
package resolve
package imports

import com.intellij.psi.{PsiField, PsiMethod, PsiReference}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PathExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

import java.nio.file.Path

class ImportsUsagesTest extends ScalaResolveTestCase {
  override def folderPath: Path = super.folderPath / "resolve" / "imports" / "simple"

  def printResults(imports: Object): Unit = {
  }

  def testStaticJava(): Unit = {
    findReferenceAtCaret() match {
      case r: PsiReference =>
        val resolve = r.resolve
        assert(resolve != null)
        assert(resolve.is[PsiField])
    }
  }

  def testStaticJavaMethod(): Unit = {
    findReferenceAtCaret() match {
      case r: PsiReference =>
        val resolve = r.resolve
        assert(resolve != null)
        assert(resolve.is[PsiMethod])
    }
  }

  def testSynthticClassesPriority(): Unit = {
    findReferenceAtCaret() match {
      case r: PsiReference =>
        val resolve = r.resolve
        assert(resolve != null)
        assert(resolve.is[ScObject])
    }
  }

  def testPredefPriority(): Unit = {
    findReferenceAtCaret() match {
      case r: PsiReference =>
        val resolve = r.resolve
        assert(resolve != null)
        assert(resolve.is[ScObject])
    }
  }

  def testaaa(): Unit = {
    findReferenceAtCaret() match {
      case r: ScReference =>
        val results = r.multiResolveScala(false)

        assert(results.length == 1)
        val srr = results(0)

        assert(srr.getElement != null)
        val imports = srr.importsUsed
        assert(imports.size == 1)
        printResults(imports)
      case _ => throw new Exception("Wrong reference!")
    }
  }


  def testSimpleImport(): Unit = {
    findReferenceAtCaret() match {
      case r: ScReference =>
        val results = r.multiResolveScala(false)

        assert(results.length == 1)
        val srr = results(0)

        assert(srr.getElement != null)
        val imports = srr.importsUsed
        assert(imports.size == 3)

        printResults(imports)
      case _ => throw new Exception("Wrong reference!")
    }
  }


  def testImportSelector(): Unit = {
    findReferenceAtCaret() match {
      case r: ScReference =>
        val results = r.multiResolveScala(false)

        assert(results.length == 1)
        val srr = results(0)

        assert(srr.getElement != null)
        val imports = srr.importsUsed
        assert(imports.size == 3)

        printResults(imports)
      case _ => throw new Exception("Wrong reference!")
    }
  }

  def testShadow1(): Unit = {
    findReferenceAtCaret() match {
      case r: ScReference =>
        val results = r.multiResolveScala(false)

        assert(results.length == 1)
        val srr = results(0)

        assert(srr.getElement != null)
        val imports = srr.importsUsed
        assert(imports.size == 1)

        printResults(imports)
      case _ => throw new Exception("Wrong reference!")
    }
  }

  def testShadow(): Unit = {
    findReferenceAtCaret() match {
      case r: ScReference =>
        val results = r.multiResolveScala(false)

        assert(results.length == 1)
        val srr = results(0)

        assert(srr.getElement != null)
        val imports = srr.importsUsed
        assert(imports.size == 2)

        printResults(imports)
      case _ => throw new Exception("Wrong reference!")
    }
  }

  def testimplicits(): Unit = {
    findReferenceAtCaret() match {
      case r: ScReference =>
        val results = r.multiResolveScala(false)

        assert(results.length == 1)
        val srr = results(0)

        assert(srr.getElement != null)
        val imports = srr.importsUsed
        assert(imports.size == 2)

        printResults(imports)
      case _ => throw new Exception("Wrong reference!")
    }
  }
}
