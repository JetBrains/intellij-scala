package org.jetbrains.plugins.scala.runner

import com.intellij.psi.{PsiClass, PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

private sealed trait MainMethodInfo {
  def sourceElement: PsiElement
}

private object MainMethodInfo {

  /**
   * example: {{{
   *  object Wrapper {
   *    def main(args: Array[String]): Unit = {}
   *  }
   * }}}
   */
  final case class Scala2Style(method: PsiMethod, obj: ScObject, sourceElement: PsiElement) extends MainMethodInfo

  /**
   * example: {{{
   * @main
   * def myMain(): Unit = {}
   * }}}
   */
  final case class Scala3Style(method: ScFunctionDefinition) extends MainMethodInfo {
    override def sourceElement: PsiElement = method
  }

  /**
   * Mainly for JavaFX which doesn't require main method (see SCL-12132)
   */
  final case class WithCustomLauncher(clazz: PsiClass) extends MainMethodInfo {
    override def sourceElement: PsiElement = clazz
  }
}