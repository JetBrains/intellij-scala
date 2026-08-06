package org.jetbrains.plugins.scala.runner

import com.intellij.codeInsight.runner.JavaMainMethodProvider
import com.intellij.psi.{PsiClass, PsiMethod}
import org.jetbrains.plugins.scala.extensions.ObjectExt

/**
 * This class is only needed during application configuration verification
 *
 * @see [[com.intellij.execution.application.ApplicationConfiguration#checkConfiguration()]]
 * @see [[com.intellij.psi.util.PsiMethodUtil#hasMainMethod(com.intellij.psi.PsiClass)]]
 * @see [[org.jetbrains.plugins.scala.runner.Scala3MainMethodSyntheticClassFinder]]
 */
final class Scala3MainMethodProvider extends JavaMainMethodProvider {

  override def isApplicable(clazz: PsiClass): Boolean = clazz.is[Scala3MainMethodSyntheticClass]

  override def hasMainMethod(clazz: PsiClass): Boolean = clazz.is[Scala3MainMethodSyntheticClass]

  // there is no actual main method in PSI tree, it only exists in generated class
  // anyway the actual method is not required during verification
  override def findMainInClass(clazz: PsiClass): PsiMethod = null
}
