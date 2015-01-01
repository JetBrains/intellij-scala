package org.jetbrains.sbt
package language.references

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.{PsiReferenceContributor, PsiReferenceRegistrar}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern

/**
 * @author Nikolay Obedin
 * @since 8/26/14.
 */
class SbtReferenceContributor extends PsiReferenceContributor {
  def registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(classOf[ScReferencePattern]), new SbtSubprojectReferenceProvider())
  }
}

