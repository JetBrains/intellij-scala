package org.jetbrains.plugins.scala.lang.refactoring.extractMethod

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import com.intellij.psi.PsiElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.01.2010
 */

class ScalaExtractMethodSettings(val methodName: String, val paramNames: Array[String], val paramTypes: Array[ScType],
        val returnTypes: Array[ScType], val visibility: String, val scope: PsiElement, val nextSibling: PsiElement,
        val elements: Array[PsiElement], val hasReturn: Boolean)