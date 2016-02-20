package org.jetbrains.plugins.scala.codeInspection

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes
import org.jetbrains.plugins.scala.lang.psi.types.ScDesignatorType

/**
  * @author Nikolay.Tropin
  */
package object methodSignature {

  private[methodSignature] def isScalaJSFacade(c: PsiClass) = {
    if (c == null) false
    else MixinNodes.linearization(c).exists {
      case ScDesignatorType(tr: ScTrait) => tr.qualifiedName == "scala.scalajs.js.Any"
      case _ => false
    }
  }

}
