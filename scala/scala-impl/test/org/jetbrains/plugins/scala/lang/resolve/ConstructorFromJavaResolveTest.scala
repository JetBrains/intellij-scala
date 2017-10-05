package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.junit.Assert._


class ConstructorFromJavaResolveTest extends ScalaResolveTestCase {

  override def folderPath(): String = s"${super.folderPath()}resolve/constructorFromJava"

  override def rootPath(): String = folderPath()

  def testScl8083(): Unit = {
    findReferenceAtCaret match {
      case st: ScStableCodeReferenceElement =>
        val variants = st.resolveAllConstructors
        assertTrue("Single resolve expected", variants.length == 1)
    }
  }
}
