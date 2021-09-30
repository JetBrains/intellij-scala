package org.jetbrains.plugins.scala
package util

import com.intellij.psi.util.PsiMethodUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

object ScalaMainMethodUtil {

  def isMainMethod(funDef: ScFunctionDefinition): Boolean =
    isScala2MainMethod(funDef) ||
      isScala3MainMethod(funDef)

  def isScala2MainMethod(funDef: ScFunctionDefinition): Boolean = {
    def isInTopLevelObject =
      funDef.containingClass match {
        case o: ScObject => o.isTopLevel
        case _           => false
      }

    def hasJavaMainWrapper = {
      funDef.getFunctionWrappers(isStatic = true, isAbstract = false)
        .headOption
        .exists(PsiMethodUtil.isMainMethod)
    }

    ScalaNamesUtil.toJavaName(funDef.name) == "main" && isInTopLevelObject && hasJavaMainWrapper
  }

  def isScala3MainMethod(funDef: ScFunctionDefinition): Boolean =
    if (funDef.isInScala3File) {
      val mainAnnotation = funDef.annotations.find(isMainAnnotation)
      mainAnnotation.isDefined
    }
    else false

  // NOTE: we could truly to "scala.main" (but currently resolve for the annotation is broken for Scala3 for some reason)
  //  maybe it's OK to do this for optimisation reasons
  private def isMainAnnotation(annotation: ScAnnotation): Boolean = {
    val text = annotation.annotationExpr.getText
    text == "main" || text == "scala.main"
  }
}
