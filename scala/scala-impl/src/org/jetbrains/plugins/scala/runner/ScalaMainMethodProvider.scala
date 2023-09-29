package org.jetbrains.plugins.scala.runner

import com.intellij.codeInsight.runner.JavaMainMethodProvider
import com.intellij.psi.{PsiClass, PsiMethod}
import org.jetbrains.plugins.scala.extensions.OptionExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.util.ScalaMainMethodUtil

class ScalaMainMethodProvider extends JavaMainMethodProvider {
  override def isApplicable(clazz: PsiClass): Boolean = clazz match {
    case _: ScTemplateDefinition => true
    case _ => false
  }

  override def findMainInClass(clazz: PsiClass): PsiMethod = {
    val effectiveObject = clazz match {
      case o: ScObject =>  o
      case t: ScTypeDefinition =>
        // - show "run" gutter on companion class as well as on the object with "main" method
        // - do not mark main class as "error" in run configurations
        ScalaPsiUtil.getCompanionModule(t).filterByType[ScObject].orNull
      case _ =>
        null
    }
    if (effectiveObject != null)
      ScalaMainMethodUtil.findScala2MainMethod(effectiveObject).orNull
    else
      null
  }

  override def hasMainMethod(clazz: PsiClass): Boolean = findMainInClass(clazz) != null
}