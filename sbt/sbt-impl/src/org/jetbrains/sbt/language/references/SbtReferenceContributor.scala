package org.jetbrains.sbt
package language.references

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.{PsiReferenceContributor, PsiReferenceRegistrar}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern

class SbtReferenceContributor extends PsiReferenceContributor {
  override def registerReferenceProviders(registrar: PsiReferenceRegistrar): Unit = {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(classOf[ScReferencePattern]), new SbtSubprojectReferenceProvider())
  }
}

