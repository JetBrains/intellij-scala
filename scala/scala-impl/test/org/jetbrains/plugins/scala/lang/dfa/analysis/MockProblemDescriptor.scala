package org.jetbrains.plugins.scala.lang.dfa.analysis

import com.intellij.psi.PsiElement

final case class MockProblemDescriptor(psiElement: PsiElement, message: String)
