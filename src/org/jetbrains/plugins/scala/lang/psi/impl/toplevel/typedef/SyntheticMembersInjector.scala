package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt

/**
 * @author Mikhail.Mutcianko
 *         date 26.12.14
 */
object SyntheticMembersInjector {

  def inject(source: ScTypeDefinition): Seq[PsiMethod] = processRules(source)

  private def processRules(source: ScTypeDefinition): Seq[PsiMethod] = {
    source match {
      // legacy macro emulation - in 2.10 quasiquotes were implemented by a compiler plugin
      // so we need to manually add QQ interpolator stub
      case c:ScClass if c.qualifiedName == "scala.StringContext" && needQQEmulation(c) =>
        val template = "def q(args: Any*): scala.reflect.runtime.universe.Tree = ???"
        try {
          val method = ScalaPsiElementFactory.createMethodWithContext(template, c, c)
          method.setSynthetic(c)
          Seq(method)
        } catch { case  e: Exception => Seq()}
      case _ => Seq()
    }
  }

  // TODO: I don't understand this enough to say what it should be, but maybe ScalaCompilerConfiguration.instanceIn(e.getProject).defaultProfile.getSettings ?
  private def needQQEmulation(e: PsiElement): Boolean = e.module.map { ScalaCompilerConfiguration.instanceIn(e.getProject).getSettingsForModule }.exists { _.plugins.exists(_.contains("paradise_2.10")) }
}
