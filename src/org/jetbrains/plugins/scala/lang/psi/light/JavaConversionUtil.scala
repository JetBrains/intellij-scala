package org.jetbrains.plugins.scala.lang.psi.light

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope

/**
 * User: Alefas
 * Date: 18.02.12
 */

object JavaConversionUtil {
  def typeText(tp: ScType, project: Project, scope: GlobalSearchScope): String = {
    val psiType = ScType.toPsi(tp, project, scope)
    psiType.getCanonicalText
  }

  def modifiers(s: ScModifierListOwner, isStatic: Boolean): String = {
    val builder = new StringBuilder

    if (isStatic) {
      builder.append("static ")
    }

    if (s.hasModifierProperty("final")) {
      builder.append("final ")
    }
    s match {
      case h: ScAnnotationsHolder =>
        if (h.hasAnnotation("scala.native") != None) builder.append("narive ")
        if (h.hasAnnotation("scala.annotation.strictfp") != None) builder.append("strictfp ")
        if (h.hasAnnotation("scala.volatile") != None) builder.append("volatile ")
        if (h.hasAnnotation("scala.transient") != None) builder.append("transient ")
      case _ =>
    }

    s.getModifierList.accessModifier match {
      case Some(a) if a.isUnqualifiedPrivateOrThis => builder.append("private ")
      case _ => builder.append("public ")
    }

    builder.toString()
  }
}
