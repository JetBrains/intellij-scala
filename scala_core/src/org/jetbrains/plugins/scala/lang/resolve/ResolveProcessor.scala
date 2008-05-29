package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi.scope._
import com.intellij.psi._
import com.intellij.lang.StdLanguages
import java.util.Set
import java.util.HashSet
import com.intellij.openapi.util.Key

class ResolveProcessor(override val kinds: Set[ResolveTargets], val name: String) extends BaseProcessor(kinds) {

  def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    val nameSet = state.get(ResolverEnv.nameKey)
    val elName = if (nameSet == null) named.getName else nameSet
    if (named != null && elName == name) {
      candidates add new ScalaResolveResult(named)
      return false //todo: for locals it is ok to terminate the walkup, later need more elaborate check
    }
    return true
  }
}

class StableMemberProcessor(override val name: String) extends ResolveProcessor (null, name) {
  override def execute(element: PsiElement, state: ResolveState): Boolean =
    element match {
      case clazz : PsiClass =>
        if ((clazz.getLanguage eq StdLanguages.JAVA) && clazz.getContainingClass != null &&
         !clazz.hasModifierProperty(PsiModifier.STATIC)) true else super.execute(element, state)
      case _ => super.execute(element, state)
    }
}

object ResolverEnv {
  val nameKey : Key[String] = Key.create("ResolverEnv.nameKey")
}