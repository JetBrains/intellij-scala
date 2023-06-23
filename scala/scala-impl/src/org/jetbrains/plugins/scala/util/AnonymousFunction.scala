package org.jetbrains.plugins.scala.util

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cachedInUserData}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.macros.MacroDef
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.util.TopLevelMembers.hasTopLevelMembers

import scala.collection.mutable

private[scala] object AnonymousFunction {
  private[scala] val isCompiledWithIndyLambdasCache: mutable.Map[PsiFile, Boolean] = mutable.HashMap.empty

  private[scala] val originalFileKey: Key[PsiFile] = Key.create[PsiFile]("compiling.evaluator.original.file")

  def isGenerateClass(elem: PsiElement): Boolean =
    isGenerateNonAnonfunClass(elem) ||
      hasTopLevelMembers(elem) ||
      isGenerateAnonfun(elem)

  def isGenerateNonAnonfunClass(elem: PsiElement): Boolean = {
    elem match {
      case newTd: ScNewTemplateDefinition if !generatesAnonClass(newTd) => false
      case _: PsiClass => true
      case _ => false
    }
  }

  private def isGenerateAnonfun(elem: PsiElement): Boolean =
    if (isCompiledWithIndyLambdas(elem.getContainingFile))
      isPartialFunction(elem) || isAnonfunInsideSuperCall(elem)
    else isGenerateAnonfun211(elem)

  def isPartialFunction(elem: PsiElement): Boolean = elem match {
    case (_: ScCaseClauses) childOf (b: ScBlockExpr) if b.isPartialFunction => true
    case _ => false
  }

  def isAnonfunInsideSuperCall(elem: PsiElement): Boolean = {
    def isInsideSuperCall(td: ScTypeDefinition) = {
      val extBlock = Option(td).map(_.extendsBlock).orNull
      PsiTreeUtil.getParentOfType(elem, classOf[ScEarlyDefinitions], classOf[ScConstructorInvocation]) match {
        case ed: ScEarlyDefinitions if ed.getParent == extBlock => true
        case c: ScConstructorInvocation if c.getParent.getParent == extBlock => true
        case _ => false
      }
    }

    val containingClass = PsiTreeUtil.getParentOfType(elem, classOf[ScTypeDefinition])
    isGenerateAnonfun211(elem) && isInsideSuperCall(containingClass)
  }

  def isGenerateAnonfun211(elem: PsiElement): Boolean = {
    def isGenerateAnonfunWithCache: Boolean = {
      if (elem == null || !elem.isValid || DumbService.isDumb(elem.getProject)) false
      else cachedInUserData("isGenerateAnonfun211.isAnonfunCached", elem, BlockModificationTracker(elem)) {
        elem match {
          case e: ScExpression if ScUnderScoreSectionUtil.underscores(e).nonEmpty => true
          case b: ScBlock if b.isPartialFunction => false //handled in isGenerateAnonfunSimple
          case e: ScExpression if ScalaPsiUtil.isByNameArgument(e) || ScalaPsiUtil.isArgumentOfFunctionType(e) => true
          case ScalaPsiUtil.MethodValue(_) => true
          case ChildOf(argExprs: ScArgumentExprList) & InsideAsync(call)
            if call.args == argExprs => true
          case _ => false
        }
      }
    }

    def isGenerateAnonfunSimple: Boolean = {
      elem match {
        case _: ScFunctionExpr => true
        case (_: ScExpression) childOf (_: ScFor) => true
        case (_: ScGuard) childOf (_: ScEnumerators) => true
        case (g: ScGenerator) childOf (enums: ScEnumerators) if !enums.generators.headOption.contains(g) => true
        case _: ScForBinding => true
        case _ => false
      }
    }

    isGenerateAnonfunSimple || isPartialFunction(elem) || isGenerateAnonfunWithCache
  }

  def generatesAnonClass(newTd: ScNewTemplateDefinition): Boolean = {
    val extBl = newTd.extendsBlock
    extBl.templateBody.nonEmpty || extBl.templateParents.exists(_.parentClauses.size > 1)
  }

  object InsideMacro {
    def unapply(elem: PsiElement): Option[ScMethodCall] = {
      elem.parentsInFile.collectFirst {
        case mc: ScMethodCall if isMacroCall(mc) => mc
      }
    }
  }

  object InsideAsync {
    def unapply(elem: PsiElement): Option[ScMethodCall] = elem match {
      case InsideMacro(call@ScMethodCall(ref: ScReferenceExpression, _)) if ref.refName == "async" => Some(call)
      case _ => None
    }
  }

  def isMacroCall(elem: PsiElement): Boolean = elem match {
    case ScMethodCall(ResolvesTo(MacroDef(_)), _) => true
    case _ => false
  }

  def isCompiledWithIndyLambdas(file: PsiFile): Boolean = {
    if (file == null) false
    else {
      val originalFile = Option(file.getUserData(originalFileKey)).getOrElse(file)
      isCompiledWithIndyLambdasCache.getOrElse(originalFile, false)
    }
  }
}
