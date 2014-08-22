package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import com.intellij.psi.{PsiElement, PsiEllipsisType, PsiManager}
import com.intellij.refactoring.changeSignature._
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.scala.extensions.ElementText
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.{JavaArrayType, ScParameterizedType, ScType}
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.ScalaExtractMethodUtils

/**
 * Nikolay.Tropin
 * 2014-08-13
 */
private[changeSignature] trait ScalaChangeSignatureUsageHandler {

  protected def handleChangedName(change: ChangeInfo, usage: UsageInfo): Unit = {
    if (!change.isNameChanged) return

    val nameId = UsageUtil.nameId(usage)
    if (nameId == null) return

    val newName = change.getNewName
    replaceNameId(nameId, newName)
  }

  protected def handleReturnTypeChange(change: ChangeInfo, usage: UsageInfo): Unit = {
    if (!change.isReturnTypeChanged) return

    val substType: ScType = UsageUtil.returnType(change, usage) match {
      case Some(result) => result
      case None => return
    }
    val newTypeElem = ScalaPsiElementFactory.createTypeElementFromText(substType.canonicalText, usage.getElement.getManager)

    val oldTypeElem = usage match {
      case ScalaOverriderUsageInfo(named) =>
        named match {
          case fun: ScFunction => fun.returnTypeElement
          case ScalaPsiUtil.inNameContext(pd: ScPatternDefinition) => pd.typeElement
          case ScalaPsiUtil.inNameContext(vd: ScVariableDefinition) => vd.typeElement
          case cp: ScClassParameter => cp.typeElement
          case _ => None
        }
      case _ => None
    }
    oldTypeElem match {
      case Some(te) =>
        val replaced = te.replace(newTypeElem)
        ScalaPsiUtil.adjustTypes(replaced)
      case None =>
    }
  }

  protected def handleParametersUsages(change: ChangeInfo, usage: UsageInfo): Unit = {
    if (change.isParameterNamesChanged || change.isParameterSetOrOrderChanged) {
      usage match {
        case ParameterUsageInfo(scParam, ref) =>
          val oldIdx = scParam.index
          val oldName = scParam.name
          val newParamName = change.getNewParameters.find(_.getOldIndex == oldIdx).map(_.getName).getOrElse(oldName)
          if (newParamName != oldName) {
            replaceNameId(ref.nameId, newParamName)
          }
        case _ =>
      }
    }
  }

  protected def handleChangedParameters(change: ChangeInfo, usage: UsageInfo): Unit = {
    if (!change.isParameterNamesChanged && !change.isParameterSetOrOrderChanged && !change.isParameterTypesChanged) return

    def inner(named: ScNamedElement): Unit = {
      val (keywordToChange, paramClauses) = named match {
        case f: ScFunction => (None, Some(f.paramClauses))
        case ScalaPsiUtil.inNameContext(pd: ScPatternDefinition) if pd.isSimple => (Some(pd.valKeyword), None)
        case ScalaPsiUtil.inNameContext(vd: ScVariableDefinition) if vd.isSimple => (Some(vd.varKeyword), None)
        case _ => return
      }
      val defKeyword = ScalaPsiElementFactory.createMethodFromText("def foo = null", named.getManager).children.find(_.getText == "def").get
      if (change.getNewParameters.length > 0) keywordToChange.foreach(_.replace(defKeyword))

      val paramsText = parameterListText(change, usage)
      val nameId = named.nameId
      val newClauses = ScalaPsiElementFactory.createParamClausesWithContext(paramsText, named, nameId)
      val result = paramClauses match {
        case Some(p) => p.replace(newClauses)
        case None => nameId.getParent.addAfter(newClauses, nameId)
      }
      ScalaPsiUtil.adjustTypes(result)
    }

    usage match {
      case ScalaOverriderUsageInfo(named) => inner(named)
      case _ =>
    }
  }

  protected def handleUsageArguments(change: ChangeInfo, usage: UsageInfo): Unit = {
    usage match {
      case m: MethodCallUsageInfo => handleMethodCallUsagesArguments(change, m)
      case r: RefExpressionUsage => handleRefUsageArguments(change, r)
      case i: InfixExprUsageInfo => handleInfixUsage(change, i)
      case p: PostfixExprUsageInfo => handlePostfixUsage(change, p)
      case _ =>
    }
  }

  protected def handleInfixUsage(change: ChangeInfo, usage: InfixExprUsageInfo): Unit = {
    val infix = usage.infix
    val newParams = change.getNewParameters
    if (newParams.length != 1) {
      infix.getArgExpr match {
        case t: ScTuple =>
          val tupleText = arguments(change, infix).map(_.getText).mkString("(", ", ", ")")
          val newTuple = ScalaPsiElementFactory.createExpressionWithContextFromText(tupleText, infix, t)
          t.replaceExpression(newTuple, removeParenthesis = false)
        case _ =>
          val methodCall = ScalaPsiElementFactory.createEquivMethodCall(infix)
          val argList = createArgList(change, methodCall)
          methodCall.args.replace(argList)
          infix.replaceExpression(methodCall, removeParenthesis = true)
      }
    } else {
      val paramExpr = arguments(change, infix)(0)
      infix.getArgExpr.replaceExpression(paramExpr, removeParenthesis = true)
    }
  }

  protected def handleRefUsageArguments(change: ChangeInfo, usage: RefExpressionUsage): Unit = {
    if (change.getNewParameters.isEmpty) return

    val ref = usage.refExpr
    val argList = createArgList(change, usage.refExpr)
    ref.addAfter(argList, ref.nameId)
  }

  protected def handlePostfixUsage(change: ChangeInfo, usage: PostfixExprUsageInfo): Unit = {
    if (change.getNewParameters.isEmpty) return

    val qualRef = ScalaPsiElementFactory.createEquivQualifiedReference(usage.postfix)
    val argList = createArgList(change, qualRef)
    qualRef.addAfter(argList, qualRef.nameId)
    usage.postfix.replaceExpression(qualRef, removeParenthesis = true)
  }

  protected def handleMethodCallUsagesArguments(change: ChangeInfo, usage: MethodCallUsageInfo): Unit = {
    val call = usage.call
    val argList = createArgList(change, call)
    call.args.replace(argList)
  }

  private def createArgList(change: ChangeInfo, invoc: ScExpression): ScArgumentExprList = {
    val args: Seq[ScExpression] = arguments(change, invoc)
    val argText = args.map(_.getText).mkString("(", ", ", ")")
    ScalaPsiElementFactory.createExpressionFromText(s"foo$argText", invoc.getManager)
            .children.collectFirst{case al: ScArgumentExprList => al}.get
  }

  private def arguments(change: ChangeInfo, invoc: ScExpression): Seq[ScExpression] = {
    val argExprs = invoc match {
      case m: MethodInvocation => m.argumentExpressions
      case ref: ScReferenceExpression => Seq.empty
      case _ => throw new IllegalArgumentException(s"Cannot find arguments for expression: ${invoc.getText}")
    }
    val nonVarargArgs = for {
      param <- change.getNewParameters.toSeq
      if !param.getTypeText.endsWith("...")
    } yield {
      val manager = invoc.getManager
      newArgumentExpression(argExprs, param, manager)
    }
    nonVarargArgs ++: varargsExprs(change, argExprs)
  }

  private def newArgumentExpression(oldArgExprs: Seq[ScExpression], newParam: ParameterInfo, manager: PsiManager): ScExpression = {
    val oldIdx = newParam.getOldIndex
    val default = newParam.getDefaultValue

    if (oldIdx >= 0 && oldIdx < oldArgExprs.size) oldArgExprs(oldIdx)
    else if (!default.isEmpty) ScalaPsiElementFactory.createExpressionFromText(default, manager)
    else ScalaPsiElementFactory.createExpressionFromText("???", manager)
  }

  private def replaceNameId(nameId: PsiElement, newName: String) {
    val newId = ScalaPsiElementFactory.createIdentifier(newName, nameId.getManager).getPsi
    nameId.replace(newId)
  }

  private def parameterListText(change: ChangeInfo, usage: UsageInfo): String = {
    val paramInfos = change.getNewParameters.toSeq
    val paramNames = paramInfos.map(_.getName)
    val method = change.getMethod
    val paramTypes = paramInfos.map {
      case jInfo: JavaParameterInfo =>
        val javaType = jInfo.createType(method, method.getManager)
        val scType = UsageUtil.substitutor(usage).subst(ScType.create(javaType, method.getProject))
        (scType, javaType) match {
          case (JavaArrayType(tpe), _: PsiEllipsisType) => tpe.canonicalText + "*"
          case _ => scType.canonicalText
        }
      case info => info.getTypeText
    }
    val params = paramNames.zip(paramTypes).map {
      case (n, t) => s"$n: $t"
    }
    params.mkString("(", ", ", ")")
  }

  private def nonVarargCount(changeInfo: ChangeInfo, args: Seq[ScExpression]): Int = {
    changeInfo match {
      case jChangeInfo: JavaChangeInfo =>
        if (!jChangeInfo.wasVararg) args.length
        else jChangeInfo.getOldParameterTypes.length - 1
      case _ => args.length
    }
  }

  private def varargsExprs(changeInfo: ChangeInfo, args: Seq[ScExpression]): Seq[ScExpression] = {
    changeInfo match {
      case jChangeInfo: JavaChangeInfo if jChangeInfo.isArrayToVarargs =>
        val oldIndex = changeInfo.getNewParameters.last.getOldIndex
        if (oldIndex < 0) Seq.empty
        else {
          val arrayExpr = args(oldIndex)
          arrayExpr match {
            case ScMethodCall(ElementText("Array"), arrayArgs) => arrayArgs
            case expr =>
              val typedText = ScalaExtractMethodUtils.typedName(expr.getText, "_*", expr.getProject, byName = false)
              val typedExpr = ScalaPsiElementFactory.createExpressionWithContextFromText(typedText, expr.getContext, expr)
              Seq(typedExpr)
          }
        }
      case jChangeInfo: JavaChangeInfo if jChangeInfo.isRetainsVarargs =>
        args.drop(nonVarargCount(changeInfo, args))
      case _ => Seq.empty
    }
  }

}
