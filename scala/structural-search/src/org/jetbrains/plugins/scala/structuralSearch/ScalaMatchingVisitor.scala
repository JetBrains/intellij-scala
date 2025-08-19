package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.psi.PsiElement
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor
import com.intellij.structuralsearch.impl.matcher.handlers.{MatchingHandler, SubstitutionHandler, TopLevelMatchingHandler}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScBlockExpr, ScIf, ScInfixExpr, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScConstructorOwner, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaPsiElement}
import org.jetbrains.plugins.scala.util.EnumSet.{EnumSet, EnumSetOps}


class ScalaMatchingVisitor(globalVisitor: GlobalMatchingVisitor) extends ScalaElementVisitor {

  private def matchOpt(patO: Option[PsiElement], psiO: Option[PsiElement]): Boolean = {
    (patO, psiO) match {
      case (Some(pat), Some(psi)) => globalVisitor.`match`(pat, psi)
      case _ => false
    }
  }

  private def matchOptOptional(patO: Option[PsiElement], psiO: Option[PsiElement]): Boolean = {
    (patO, psiO) match {
      case (Some(pat), Some(psi)) => globalVisitor.`match`(pat, psi)
      case (None, _) => true
      case _ => false
    }
  }

  private def matchBody(patO: Option[PsiElement], psiO: Option[PsiElement]): Boolean = {
      (patO, psiO) match {
        case (Some(pat: ScBlockExpr), Some(psi: ScBlockExpr)) =>
          globalVisitor.matchSequentially(pat.getFirstChild, psi.getFirstChild)
        case (Some(pat: ScBlockExpr), Some(psi)) =>
          pat.statements.size == 1 && globalVisitor.`match`(pat.statements.head, psi)
        case (Some(pat), Some(psi: ScBlockExpr)) =>
          globalVisitor.matchSequentially(Array(pat), psi.statements.toArray[PsiElement])
        case (Some(pat), Some(psi)) =>
          globalVisitor.`match`(pat, psi)
        case _ => false
      }
  }

  private def checkModifier(pat: EnumSet[ScalaModifier], psi: EnumSet[ScalaModifier]): Boolean =
    pat.toArray.forall(p => psi.contains(p))

  private def matchTextOrVariable(el1: PsiElement, el2: PsiElement, handler: MatchingHandler): Boolean = {
    handler match {
      case substHandler: SubstitutionHandler => substHandler.validate(el2, globalVisitor.getMatchContext)
      case topLevel: TopLevelMatchingHandler =>
        topLevel.getDelegate match {
          case substHandler: SubstitutionHandler => substHandler.validate(el2, globalVisitor.getMatchContext)
          case _ => globalVisitor.matchText(el1, el2)
        }
      case _ => globalVisitor.matchText(el1, el2)
    }
  }

  private def getHandler(element: PsiElement) =
    globalVisitor.getMatchContext.getPattern.getHandler(element)

  private def rememberVarMatchIfResult(handler: MatchingHandler, matchedEl: PsiElement): Unit = {
    if (globalVisitor.getResult) {
      handler match {
        case substHandler: SubstitutionHandler =>
          substHandler.handle(matchedEl, globalVisitor.getMatchContext)
        case topLevel: TopLevelMatchingHandler =>
          topLevel.getDelegate match {
            case substHandler: SubstitutionHandler =>
              substHandler.handle(matchedEl, globalVisitor.getMatchContext)
            case _ =>
          }
        case _ =>
      }
    }
  }

  override def visitTypeDefinition(typedef: ScTypeDefinition): Unit = {
    if (!globalVisitor.getElement.is[ScTypeDefinition]) return
    val other = globalVisitor.getElement.asInstanceOf[ScTypeDefinition]

    val handler = getHandler(typedef)
    val keywordMatch = typedef.keywordPrefix == other.keywordPrefix
    val modifierMatch = checkModifier(typedef.getModifierList.modifiers, other.getModifierList.modifiers)
    val nameMatch = matchTextOrVariable(typedef.getNameIdentifier, other.getNameIdentifier, handler)
    val functionsMatch = globalVisitor.matchInAnyOrder(typedef.functions.toArray[PsiElement], other.functions.toArray[PsiElement])
    val constructorsMatch = (typedef, other) match {
      case (typedef: ScConstructorOwner, other: ScConstructorOwner) =>
        (typedef.constructor.exists(c => c.parameters.isEmpty) || matchOptOptional(typedef.constructor, other.constructor))
        && globalVisitor.matchInAnyOrder(typedef.secondaryConstructors.toArray[PsiElement], other.secondaryConstructors.toArray[PsiElement])
      case _ => true
    }

    // TODO start parsing function
    // ignore the order of nearly all elements inside

    globalVisitor.setResult(keywordMatch && modifierMatch && nameMatch && functionsMatch && constructorsMatch)
    rememberVarMatchIfResult(handler, other.getNameIdentifier)
  }

  override def visitFunction(fun: ScFunction): Unit = {
    if (!globalVisitor.getElement.is[ScFunction]) return
    val other = globalVisitor.getElement.asInstanceOf[ScFunction]

    val handler = getHandler(fun)
    val modifierMatch = checkModifier(fun.getModifierList.modifiers, other.getModifierList.modifiers)
    val nameMatch = matchTextOrVariable(fun.getNameIdentifier, other.getNameIdentifier, handler)
    val typeParamsMatch = globalVisitor.matchSequentially(fun.typeParameters.toArray[PsiElement], other.typeParameters.toArray[PsiElement])
    val paramsMatch = globalVisitor.matchSequentially(fun.parameters.toArray[PsiElement], other.parameters.toArray[PsiElement])
    val rTypeMatch = matchOptOptional(fun.returnTypeElement, other.returnTypeElement)
    val bodyMatch = {
      fun match {
        case declPat: ScFunctionDefinition =>
          other match {
            case declOther: ScFunctionDefinition =>
              matchBody(declPat.body, declOther.body)
            case _ => false
          }
        case _ => true
      }
    }

    globalVisitor.setResult(modifierMatch && typeParamsMatch && paramsMatch && rTypeMatch && bodyMatch)
    rememberVarMatchIfResult(handler, other.getNameIdentifier)
  }

  override def visitParameter(parameter: ScParameter): Unit = {
    if (!globalVisitor.getElement.is[ScParameter]) {
      globalVisitor.setResult(false)
      return
    }
    val other = globalVisitor.getElement.asInstanceOf[ScParameter]

    val handler = getHandler(parameter)
    val modifierMatch = checkModifier(parameter.getModifierList.modifiers, other.getModifierList.modifiers)
    val typeMatch = matchOptOptional(parameter.typeElement, other.typeElement)
    val identMatch = matchTextOrVariable(parameter.getIdentifyingElement, other.getIdentifyingElement, handler)

    globalVisitor.setResult(modifierMatch && typeMatch && identMatch)
    rememberVarMatchIfResult(handler, other.getNameIdentifier)
  }

  override def visitIf(ifPat: ScIf): Unit = {
    val ifPsi = globalVisitor.getElement.asInstanceOf[ScIf]

    val condMatch = matchOpt(ifPat.condition, ifPsi.condition)
    val thenMatch = matchBody(ifPat.thenExpression, ifPsi.thenExpression)
    val elseMatch = (ifPat.elseExpression, ifPsi.elseExpression) match {
      case (None, None) => true
      case _ => matchBody(ifPat.elseExpression, ifPsi.elseExpression)
    }
    globalVisitor.setResult(condMatch && thenMatch && elseMatch)
  }

  override def visitInfixExpression(infixPat: ScInfixExpr): Unit = {
    visitMethodInvocation(infixPat)
  }

  override def visitLiteral(lPat: ScLiteral): Unit =
    globalVisitor.setResult(globalVisitor.matchText(lPat, globalVisitor.getElement))

  override def visitReferenceExpression(refPat: ScReferenceExpression): Unit = {
    val context = globalVisitor.getMatchContext
    val pattern = context.getPattern
    val other = globalVisitor.getElement
    pattern.getHandlerSimple(refPat) match {
      case substHand: SubstitutionHandler =>
        if (globalVisitor.setResult(substHand.validate(other, context)))
            substHand.addResult(other, context)
      case _ =>
        other match {
          case refExpr: ScReferenceExpression => globalVisitor.setResult(globalVisitor.`match`(refPat.getFirstChild, globalVisitor.getElement.getFirstChild))
          case _ => visitElement(refPat)
        }
    }
  }

  override def visitMethodCallExpression(call: ScMethodCall): Unit = visitMethodInvocation(call)

  def visitMethodInvocation(call: MethodInvocation): Unit = {
    val thisPat = call.thisExpr
    val invokedPat = call.getInvokedExpr.getLastChild
    val parsPat = call.argumentExpressions

    val other = globalVisitor.getElement.asInstanceOf[MethodInvocation]
    val thisPsi = other.thisExpr
    val invokedPsi = other.getInvokedExpr.getLastChild
    val parsPsi = other.argumentExpressions

    val thisMatch = globalVisitor.matchOptionally(thisPat.orNull, thisPsi.orNull)
    val methodMatch = globalVisitor.`match`(invokedPat, invokedPsi)
    val parsMatch = globalVisitor.matchSequentially(parsPat.toArray[PsiElement], parsPsi.toArray[PsiElement])
    globalVisitor.setResult(thisMatch && methodMatch && parsMatch)
  }

  override def visitScalaElement(element: ScalaPsiElement): Unit = visitElement(element)

  override def visitElement(elementPat: PsiElement): Unit = {
    val other = globalVisitor.getElement

    getHandler(elementPat) match {
      case substHandler: SubstitutionHandler =>
        globalVisitor.setResult(substHandler.handle(other, globalVisitor.getMatchContext))
      case topLevel: TopLevelMatchingHandler =>
        topLevel.getDelegate match {
          case substHandler: SubstitutionHandler =>
            globalVisitor.setResult(substHandler.handle(other, globalVisitor.getMatchContext))
          case _ =>
            globalVisitor.setResult(globalVisitor.matchText(elementPat.getText, other.getText))
        }
      case _ =>
        globalVisitor.setResult(globalVisitor.matchText(elementPat.getText, other.getText))
    }

//    val handler = elementPat.getUserData(CompiledPattern.HANDLER_KEY)
//    handler match {
//      case substHandler: SubstitutionHandler =>
//        globalVisitor.setResult(substHandler.handle(other, globalVisitor.getMatchContext))
//      case null =>
//        getHandler(elementPat) match {
//          case substHandler: SubstitutionHandler =>
//            globalVisitor.setResult(substHandler.handle(other, globalVisitor.getMatchContext))
//          case _ =>
//            globalVisitor.setResult(globalVisitor.matchText(elementPat.getText, other.getText))
//        }
//      case _ =>
//        globalVisitor.setResult(handler.`match`(elementPat, other, globalVisitor.getMatchContext))
//    }
  }
}