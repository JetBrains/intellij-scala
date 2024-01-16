package org.jetbrains.plugins.scala.runner

import com.intellij.codeInsight.runner.JavaMainMethodProvider
import com.intellij.psi.{PsiClass, PsiMethod}
import org.jetbrains.plugins.scala.extensions.OptionExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.util.ScalaMainMethodUtil

class ScalaMainMethodProvider extends JavaMainMethodProvider {
  override def isApplicable(clazz: PsiClass): Boolean = clazz match {
    case _: ScTemplateDefinition => true
    /**
     * Instance of [[PsiClassWrapper]] can be passed by [[com.intellij.execution.application.ApplicationConfiguration#checkClass]]
     *
     * To run Scala programs [[com.intellij.execution.application.ApplicationConfiguration]] is used.
     * This run configuration is primarily written for Java language and we "piggyback" on it.
     * Because of that, the configuration operates not with the [[ScObject]]
     * but with a special wrapper class [[PsiClassWrapper]], which provides a "view" of the object in JVM.
     */
    case _: PsiClassWrapper => true
    case _ => false
  }

  override def findMainInClass(clazz: PsiClass): PsiMethod = {
    val objectToCheck: ScTypeDefinition with ScObject = clazz match {
      case o: ScObject => o
      case t: ScTypeDefinition =>
        // - show "run" gutter on companion class as well as on the object with "main" method
        // - do not mark main class as "error" in run configurations
        ScalaPsiUtil.getCompanionModule(t).filterByType[ScObject].orNull
      // see comment in `isApplicable`
      case w: PsiClassWrapper => w.definition match {
        case o: ScObject => o
        case _ => null
      }
      case _ =>
        null
    }

    if (objectToCheck != null)
      ScalaMainMethodUtil.findScala2MainMethod(objectToCheck).orNull
    else
      null
  }

  override def hasMainMethod(clazz: PsiClass): Boolean = findMainInClass(clazz) != null
}