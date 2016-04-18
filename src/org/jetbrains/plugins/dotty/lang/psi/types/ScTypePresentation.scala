package org.jetbrains.plugins.dotty.lang.psi.types

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.lang.psi.types._

/**
  * @author adkozlov
  */
object ScTypePresentation extends org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation {
  override implicit lazy val typeSystem = DottyTypeSystem

  override protected def typeText(`type`: ScType,
                                  nameFun: (PsiNamedElement) => String,
                                  nameWithPointFun: (PsiNamedElement) => String) = "DottyType"
}
