package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.VisitorWrapper
import org.jetbrains.plugins.scala.codeInspection.{AbstractInspection, InspectionsUtil}

/**
 * Pavel Fatin
 */
abstract class AbstractMethodSignatureInspection(id: String, name: String) extends AbstractInspection(id, name) {
  override final def getGroupDisplayName = InspectionsUtil.MethodSignature
}