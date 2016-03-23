package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.junit.Assert._
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class ConstructorFromJavaResolveTest extends FailedResolveTest("constructorFromJava") {

  def testScl8083(): Unit = {
    findReferenceAtCaret match {
      case st: ScStableCodeReferenceElement =>
        val variants = st.resolveAllConstructors
        assertTrue("Single resolve expected", variants.length == 1)
    }
  }
}
