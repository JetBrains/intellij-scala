package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import com.incors.plaf.n
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
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
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

  protected def handleReturnTypeChange(change: ChangeInfo, usage: ScalaOverriderUsageInfo): Unit = {
    if (!change.isReturnTypeChanged) return

    val substType: ScType = UsageUtil.returnType(change, usage) match {
      case Some(result) => result
      case None => return
    }
    val newTypeElem = ScalaPsiElementFactory.createTypeElementFromText(substType.canonicalText, usage.overrider.getManager)

    val oldTypeElem = usage match {
      case ScalaOverriderUsageInfo(scUsage) =>
        scUsage.overrider match {
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

  protected def handleChangedParameters(change: ChangeInfo, usage: ScalaOverriderUsageInfo): Unit = {
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
      case ScalaOverriderUsageInfo(scUsage) => inner(scUsage.overrider)
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
          val tupleText = arguments(change, usage).map(_.getText).mkString("(", ", ", ")")
          val newTuple = ScalaPsiElementFactory.createExpressionWithContextFromText(tupleText, infix, t)
          t.replaceExpression(newTuple, removeParenthesis = false)
        case _ =>
          val methodCall = ScalaPsiElementFactory.createEquivMethodCall(infix)
          val argList = createArgList(change, usage)
          methodCall.args.replace(argList)
          infix.replaceExpression(methodCall, removeParenthesis = true)
      }
    } else {
      val paramExpr = arguments(change, usage)(0)
      infix.getArgExpr.replaceExpression(paramExpr, removeParenthesis = true)
    }
  }

  protected def handleRefUsageArguments(change: ChangeInfo, usage: RefExpressionUsage): Unit = {
    if (change.getNewParameters.isEmpty) return

    val ref = usage.refExpr
    val argList = createArgList(change, usage)
    ref.addAfter(argList, ref.nameId)
  }

  protected def handlePostfixUsage(change: ChangeInfo, usage: PostfixExprUsageInfo): Unit = {
    if (change.getNewParameters.isEmpty) return

    val qualRef = ScalaPsiElementFactory.createEquivQualifiedReference(usage.postfix)
    val argList = createArgList(change, usage)
    qualRef.addAfter(argList, qualRef.nameId)
    usage.postfix.replaceExpression(qualRef, removeParenthesis = true)
  }

  protected def handleMethodCallUsagesArguments(change: ChangeInfo, usage: MethodCallUsageInfo): Unit = {
    val call = usage.call
    val argList = createArgList(change, usage)
    call.args.replace(argList)
  }

  private def createArgList(change: ChangeInfo, methodUsage: MethodUsageInfo): ScArgumentExprList = {
    val args: Seq[ScExpression] = arguments(change, methodUsage)
    val argText = args.map(_.getText).mkString("(", ", ", ")")
    ScalaPsiElementFactory.createExpressionFromText(s"foo$argText", methodUsage.expr.getManager)
            .children.collectFirst{case al: ScArgumentExprList => al}.get
  }

  private def arguments(change: ChangeInfo, methodUsage: MethodUsageInfo): Seq[ScExpression] = {
    if (change.getNewParameters.length == 0) return Seq.empty

    val oldArgsInfo = methodUsage.argsInfo
    val manager = methodUsage.expr.getManager
    val nonVarargArgs = for {
      param <- change.getNewParameters.toSeq
      if !param.getTypeText.endsWith("...")
      argExpr <- newArgumentExpression(oldArgsInfo, param, manager)
    } yield {
      argExpr
    }
    nonVarargArgs ++: varargsExprs(change, oldArgsInfo)
  }

  private def newArgumentExpression(argsInfo: OldArgsInfo, newParam: ParameterInfo, manager: PsiManager): Option[ScExpression] = {
    val oldIdx = newParam.getOldIndex
    val default = newParam.getDefaultValue

    if (oldIdx < 0) {
      return Some(ScalaPsiElementFactory.createExpressionFromText(if (!default.isEmpty) default else "???", manager))
    }

    val oldArg = argsInfo.byOldParameterIndex.get(oldIdx)
    if (oldArg.isEmpty || oldArg.get.size > 1) return None //parameter has no associated argExpr, so it was a default argument or it's a vararg

    oldArg.get.headOption
  }

  private def replaceNameId(nameId: PsiElement, newName: String) {
    val newId = ScalaPsiElementFactory.createIdentifier(newName, nameId.getManager).getPsi
    nameId.replace(newId)
  }

  private def parameterListText(change: ChangeInfo, usage: ScalaOverriderUsageInfo): String = {
    def paramType(paramInfo: ParameterInfo) = {
      val method = change.getMethod
      paramInfo match {
        case jInfo: JavaParameterInfo =>
          val javaType = jInfo.createType(method, method.getManager)
          val scType = UsageUtil.substitutor(usage).subst(ScType.create(javaType, method.getProject))
          (scType, javaType) match {
            case (JavaArrayType(tpe), _: PsiEllipsisType) => tpe.canonicalText + "*"
            case _ => scType.canonicalText
          }
        case info => info.getTypeText
      }
    }
    def scalaDefaultValue(paramInfo: ParameterInfo): Option[String] = {
      val oldIdx = paramInfo.getOldIndex
      if (oldIdx > 0) usage.defaultValues(oldIdx) else None
    }

    val paramInfos = change.getNewParameters.toSeq
    val project = change.getMethod.getProject
    val params = paramInfos.map { p =>
      val typedName = ScalaExtractMethodUtils.typedName(p.getName, paramType(p), project, byName = false)
      val default = scalaDefaultValue(p).fold("")(" = " + _)
      typedName + default
    }
    params.mkString("(", ", ", ")")
  }

  private def varargsExprs(changeInfo: ChangeInfo, argsInfo: OldArgsInfo): Seq[ScExpression] = {
    val parameters = changeInfo.getNewParameters

    val oldIndex = parameters.last.getOldIndex
    changeInfo match {
      case jChangeInfo: JavaChangeInfo if jChangeInfo.isArrayToVarargs =>
        if (oldIndex < 0) Seq.empty
        else {
          val (arrayExpr, wasNamed) = argsInfo.byOldParameterIndex.get(oldIndex) match {
            case Some(Seq(ScAssignStmt(_, Some(expr)))) => (expr, true)
            case Some(Seq(expr)) => (expr, false)
            case _ => return Seq.empty
          }
          arrayExpr match {
            case ScMethodCall(ElementText("Array"), arrayArgs) => arrayArgs
            case expr =>
              val typedText = ScalaExtractMethodUtils.typedName(expr.getText, "_*", expr.getProject, byName = false)
              val naming = if (wasNamed) parameters.last.getName + " = " else ""
              val text = naming + typedText
              val typedExpr = ScalaPsiElementFactory.createExpressionWithContextFromText(text, expr.getContext, expr)
              Seq(typedExpr)
          }
        }
      case jChangeInfo: JavaChangeInfo if jChangeInfo.isRetainsVarargs =>
        argsInfo.byOldParameterIndex.getOrElse(oldIndex, Seq.empty)
      case _ => Seq.empty
    }
  }

}
