package org.jetbrains.plugins.scala.lang.psi.api.statements

import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.getPsiElementId

/**
  * @author adkozlov
  */
package object params {

  implicit class PsiTypeParameterExt(val typeParameter: PsiTypeParameter) extends AnyVal {
    def nameAndId = getPsiElementId(typeParameter)
  }

}
