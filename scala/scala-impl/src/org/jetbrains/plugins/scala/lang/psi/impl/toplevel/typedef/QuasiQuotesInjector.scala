package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettings

class QuasiQuotesInjector extends SyntheticMembersInjector {
  override def injectFunctions(source: ScTypeDefinition): Seq[String] = {
    source match {
      // legacy macro emulation - in 2.10 quasiquotes were implemented by a compiler plugin
      // so we need to manually add QQ interpolator stub
      case c: ScClass if c.qualifiedName == "scala.StringContext" && needQQEmulation(c) =>
        Seq("def q(args: Any*): _root_.scala.reflect.runtime.universe.Tree = ???")
      case _ => Seq.empty
    }
  }

  private def needQQEmulation(e: PsiElement) =
    ScalaCompilerSettings.forElement(e).exists(_.plugins.exists(_.hasPluginJarWithName("paradise_2.10")))
}
