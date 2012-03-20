package org.jetbrains.plugins.scala.lang.completion.lookups

import com.intellij.codeInsight.lookup.LookupItem
import com.intellij.psi.PsiMethod

/**
 * @author Alefas
 * @since 19.03.12
 */
class ScalaMethodLookupElement(method: PsiMethod, lookupString: String) extends LookupItem[PsiMethod](method, lookupString) {

}
