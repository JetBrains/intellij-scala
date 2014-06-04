package org.jetbrains.plugins.scala
package codeInsight.generation.ui

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScTemplateDefinition, ScObject}
import com.intellij.refactoring.classMembers.MemberInfoBase
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import com.intellij.psi.{PsiModifier, PsiMethod}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition, ScFunctionDeclaration}
import com.intellij.lang.java.JavaLanguage
import org.jetbrains.plugins.scala.lang.structureView.ScalaElementPresentation
import org.jetbrains.plugins.scala.lang.refactoring.ui.ScalaMemberInfoBase

/**
 * Nikolay.Tropin
 * 8/20/13
 */
class ScalaMemberInfo(member: ScNamedElement)
        extends ScalaMemberInfoBase[ScNamedElement](member: ScNamedElement)
