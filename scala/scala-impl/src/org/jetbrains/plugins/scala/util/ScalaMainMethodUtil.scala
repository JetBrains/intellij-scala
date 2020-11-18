package org.jetbrains.plugins.scala
package util

import com.intellij.codeInsight.runner.JavaMainMethodProvider
import com.intellij.psi.util.{PsiMethodUtil, PsiTreeUtil}
import com.intellij.psi.{PsiClass, PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData

object ScalaMainMethodUtil {

  def findMainClass(element: PsiElement): Option[PsiClass] = findMainClassAndSourceElem(element).map(_._1)

  def findMainClassAndSourceElem(element: PsiElement): Option[(PsiClass, PsiElement)] = {
    val mainMethod = findContainingMainMethod(element)
    mainMethod match {
      case Some(funDef) =>
        Some((funDef.containingClass, funDef.getFirstChild))
      case None =>
        val objectWithMain = findObjectWithMain(element)
        objectWithMain match {
          case Some(obj) =>
            val sourceElem =
              if (PsiTreeUtil.isAncestor(obj, element, false)) obj.fakeCompanionClassOrCompanionClass
              else element.getContainingFile
            Some((obj, sourceElem))
          case None =>
            findMainClassWithProvider(element) match {
              case Some(c) => Some((c, c))
              case _ => None
            }
        }
    }
  }

  private def findContainingMainMethod(elem: PsiElement): Option[ScFunctionDefinition] = {
    elem.withParentsInFile.collectFirst {
      case funDef: ScFunctionDefinition if isMainMethod(funDef) => funDef
    }
  }

  def findContainingScala3MainMethod(elem: PsiElement): Option[ScFunctionDefinition] =
    containingScala3MainMethodCandidates(elem).headOption

  def containingScala3MainMethodCandidates(elem: PsiElement): Iterator[ScFunctionDefinition] =
    if (elem.isInScala3File)
      elem.withParentsInFile.collect {
        case funDef: ScFunctionDefinition if isScala3MainMethod(funDef) => funDef
      }
    else
      Iterator.empty

  def findObjectWithMain(element: PsiElement): Option[ScObject] = {
    def findTopLevel: Option[ScObject] = element.containingScalaFile.flatMap { file =>
      file.typeDefinitions.collectFirst {
        case o: ScObject if hasMainMethod(o) => o
      }
    }

    stableObject(element).filter(hasMainMethod) orElse findTopLevel
  }

  def hasMainMethodFromProviders(c: PsiClass): Boolean = JavaMainMethodProvider.EP_NAME.getExtensions.exists(_.hasMainMethod(c))

  def hasMainMethod(obj: ScObject): Boolean = findMainMethod(obj).isDefined

  private def findMainClassWithProvider(element: PsiElement): Option[PsiClass] = {
    val classes = element.withParentsInFile.collect {
      case td: ScTypeDefinition => td
    }
    classes.find(hasMainMethodFromProviders)
  }

  def findMainMethod(obj: ScObject): Option[PsiMethod] = {

    def declaredMain(obj: ScObject): Option[ScFunctionDefinition] = {
      obj.functions.collectFirst {
        case funDef: ScFunctionDefinition if isMainMethod(funDef) => funDef
      }
    }

    @CachedInUserData(obj, BlockModificationTracker(obj))
    def findMainMethodInner(): Option[PsiMethod] = {
      declaredMain(obj) orElse Option(PsiMethodUtil.findMainMethod(new PsiClassWrapper(obj, obj.qualifiedName, obj.name)))
    }

    if (!ScalaPsiUtil.hasStablePath(obj)) None
    else findMainMethodInner()
  }

  def isMainMethod(funDef: ScFunctionDefinition): Boolean = {
    def isInStableObject = stableObject(funDef).contains(funDef.containingClass)

    def hasJavaMainWrapper =
      funDef.getFunctionWrappers(isStatic = true, isAbstract = false)
        .headOption
        .exists(PsiMethodUtil.isMainMethod)

    ScalaNamesUtil.toJavaName(funDef.name) == "main" && isInStableObject && hasJavaMainWrapper
  }

  def isScala3MainMethod(funDef: ScFunctionDefinition): Boolean =
    if (funDef.isInScala3File) {
      val mainAnnotation = funDef.annotations.find(isMainAnnotation)
      mainAnnotation.isDefined
    }
    else false

  // TODO: resolve properly to "scala.main" (currently resolve for the annotation is broken for Scala3 for some reason)
  private def isMainAnnotation(annotation: ScAnnotation): Boolean = {
    val text = annotation.annotationExpr.getText
    text == "main" || text == "scala.main"
  }

  private def stableObject(element: PsiElement): Option[ScObject] = element.withParentsInFile.collectFirst {
    case obj: ScObject if ScalaPsiUtil.hasStablePath(obj) => obj
  }
}
