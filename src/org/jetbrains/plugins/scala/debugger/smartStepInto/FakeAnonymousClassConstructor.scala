package org.jetbrains.plugins.scala.debugger.smartStepInto

import javax.swing.Icon

import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.types._

/**
 * @author Nikolay.Tropin
 */
class FakeAnonymousClassConstructor(templ: ScNewTemplateDefinition, interfaceName: String)
  extends FakePsiMethod(templ, interfaceName, Array.empty, Unit, _ => false) {
  override def isConstructor: Boolean = true

  override def getIcon(flags: Int): Icon = Icons.CLASS

  override def equals(obj: scala.Any): Boolean = obj match {
    case fake: FakeAnonymousClassConstructor =>
      fake.navElement == this.navElement && fake.getName == this.getName
    case _ => false
  }

  override def hashCode() = navElement.hashCode() + 31 * getName.hashCode
}

