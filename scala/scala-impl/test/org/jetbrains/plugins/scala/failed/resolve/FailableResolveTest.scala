package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.base.FailableTest
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, ScalaResolveTestCase}
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert._

/**
  * @author Nikolay.Tropin
  */
abstract class FailableResolveTest(dirName: String) extends ScalaResolveTestCase with FailableTest {

  override def folderPath: String = s"${super.folderPath}resolve/failed/$dirName"

  override def sourceRootPath: String = folderPath

  override protected def shouldPass: Boolean = false

  def doTest(): Unit = {
    findReferenceAtCaret() match {
      case ref: ScReference =>
        val variants = ref.multiResolveScala(false)
        if (shouldPass) {
          assertTrue(s"Single resolve expected, was: ${variants.length}", variants.length == 1 &&
            variants.head.isValidResult && additionalAsserts(variants, ref))
        } else {
          assertFalse(failingPassed, variants.length == 1 && variants.head.isValidResult && additionalAsserts(variants, ref))
        }
    }
  }

  protected def additionalAsserts(variants: Array[ScalaResolveResult], ref: ScReference): Boolean = true
}
