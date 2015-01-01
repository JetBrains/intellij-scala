package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import com.intellij.psi._
import com.intellij.refactoring.changeSignature._
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.scala.codeInsight.intention.types.Update
import org.jetbrains.plugins.scala.extensions.{ChildOf, ElementText}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.result.Success
import org.jetbrains.plugins.scala.lang.psi.types.{JavaArrayType, ScFunctionType, ScType}
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.changeInfo.ScalaChangeInfo
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.ScalaExtractMethodUtils
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.refactoring.rename.ScalaRenameUtil

import scala.collection.mutable.ListBuffer

/**
 * Nikolay.Tropin
 * 2014-08-13
 */
private[changeSignature] trait ScalaChangeSignatureUsageHandler {

  protected def handleChangedName(change: ChangeInfo, usage: UsageInfo): Unit = {
    if (!change.isNameChanged) return
    
    val nameId = usage match {
      case ScalaNamedElementUsageInfo(scUsage) => scUsage.namedElement.nameId
      case MethodCallUsageInfo(ref, _) => ref.nameId
      case RefExpressionUsage(r) => r.nameId
      case InfixExprUsageInfo(i) => i.operation.nameId
      case PostfixExprUsageInfo(p) => p.operation.nameId
      case AnonFunUsageInfo(_, ref) => ref.nameId
      case ImportUsageInfo(ref) => ref.nameId
      case _ => null
    }

    nameId match {
      case null =>
      case ChildOf(ref: ScReferenceElement) if ScalaRenameUtil.isAliased(ref) =>
      case _ =>
        val newName = change.getNewName
        replaceNameId(nameId, newName)
    }
  }

  protected def handleVisibility(change: ChangeInfo, usage: ScalaNamedElementUsageInfo): Unit = {
    val visibility = change match {
      case j: JavaChangeInfo => j.getNewVisibility
      case _ => return
    }
    val member = ScalaPsiUtil.nameContext(usage.namedElement) match {
      case cl: ScClass => cl.constructor.getOrElse(return)
      case m: ScModifierListOwner => m
      case _ => return
    }

    ScalaPsiUtil.changeVisibility(member, visibility)
  }

  protected def handleReturnTypeChange(change: ChangeInfo, usage: ScalaNamedElementUsageInfo): Unit = {
    val element = usage.namedElement
    if (!change.isReturnTypeChanged) return

    val substType: ScType = UsageUtil.returnType(change, usage) match {
      case Some(result) => result
      case None => return
    }
    val newTypeElem = ScalaPsiElementFactory.createTypeElementFromText(substType.canonicalText, element.getManager)

    val oldTypeElem = element match {
      case fun: ScFunction => fun.returnTypeElement
      case ScalaPsiUtil.inNameContext(pd: ScPatternDefinition) => pd.typeElement
      case ScalaPsiUtil.inNameContext(vd: ScVariableDefinition) => vd.typeElement
      case cp: ScClassParameter => cp.typeElement
      case _ => None
    }
    oldTypeElem match {
      case Some(te) =>
        val replaced = te.replace(newTypeElem)
        ScalaPsiUtil.adjustTypes(replaced)
      case None =>
        val (context, anchor) = ScalaPsiUtil.nameContext(element) match {
          case f: ScFunction => (f, f.paramClauses)
          case p: ScPatternDefinition => (p, p.pList)
          case v: ScVariableDefinition => (v, v.pList)
          case cp: ScClassParameter => (cp.getParent, cp)
          case ctx => (ctx, ctx.getLastChild)
        }
        Update.addTypeAnnotation(substType, context, anchor)
    }
  }

  protected def handleParametersUsage(change: ChangeInfo, usage: ParameterUsageInfo): Unit = {
    if (change.isParameterNamesChanged || change.isParameterSetOrOrderChanged) {
      replaceNameId(usage.ref.getElement, usage.newName)
    }
  }

  def handleAnonFunUsage(change: ChangeInfo, usage: AnonFunUsageInfo): Unit = {
    if (!change.isParameterSetOrOrderChanged) return
    val jChange = change match {
      case j: JavaChangeInfo => j
      case _ => return
    }
    val paramTypes = usage.expr.getType() match {
      case Success(ScFunctionType(_, pTypes), _) => pTypes
      case _ => Seq.empty
    }
    val (names, exprText) = usage.expr match {
      case inv: MethodInvocation =>
        var paramsBuf = Seq[String]()
        for {
          (arg, param) <- inv.matchedParameters.sortBy(_._2.index)
          if ScUnderScoreSectionUtil.isUnderscore(arg)
        } {
          val paramName =
            if (!param.name.isEmpty) param.name
            else param.nameInCode match {
              case Some(n) => n
              case None => NameSuggester.suggestNamesByType(param.paramType)(0)
            }
          paramsBuf = paramsBuf :+ paramName
          val text = ScalaPsiElementFactory.createExpressionFromText(paramName, arg.getManager)
          arg.replaceExpression(text, removeParenthesis = true)
        }
        (paramsBuf, inv.getText)
      case _ =>
        val paramNames = jChange.getOldParameterNames.toSeq
        val refText = usage.ref.getText
        val argText = paramNames.mkString("(", ", ", ")")
        (paramNames, s"$refText$argText")
    }

    val params =
      if (paramTypes.size == names.size)
        names.zip(paramTypes).map {
          case (name, tpe) =>
            ScalaExtractMethodUtils.typedName(name, tpe.canonicalText, usage.expr.getProject)
        }
      else names
    val clause = params.mkString("(", ", ", ")")
    val newFunExprText = s"$clause => $exprText"
    val funExpr = ScalaPsiElementFactory.createExpressionFromText(newFunExprText, usage.expr.getManager)
    val replaced = usage.expr.replaceExpression(funExpr, removeParenthesis = true).asInstanceOf[ScFunctionExpr]
    ScalaPsiUtil.adjustTypes(replaced)
    replaced.result match {
      case Some(infix: ScInfixExpr) =>
        handleInfixUsage(change, InfixExprUsageInfo(infix))
      case Some(mc @ ScMethodCall(ref: ScReferenceExpression, _)) =>
        handleMethodCallUsagesArguments(change, MethodCallUsageInfo(ref, mc))
      case _ =>
    }
  }

  protected def handleChangedParameters(change: ChangeInfo, usage: ScalaNamedElementUsageInfo): Unit = {
    if (!change.isParameterNamesChanged && !change.isParameterSetOrOrderChanged && !change.isParameterTypesChanged) return

    def inner(named: ScNamedElement): Unit = {

      val keywordToChange = named match {
        case _: ScFunction | _: ScClass => None
        case ScalaPsiUtil.inNameContext(pd: ScPatternDefinition) if pd.isSimple => Some(pd.valKeyword)
        case ScalaPsiUtil.inNameContext(vd: ScVariableDefinition) if vd.isSimple => Some(vd.varKeyword)
        case _ => return
      }
      keywordToChange.foreach { kw =>
        val defKeyword = ScalaPsiElementFactory.createMethodFromText("def foo {}", named.getManager).children.find(_.getText == "def").get
        if (change.getNewParameters.length > 0) kw.replace(defKeyword)
      }

      val paramsText = parameterListText(change, usage)
      val nameId = named.nameId
      val newClauses = named match {
        case cl: ScClass =>
          ScalaPsiElementFactory.createClassParamClausesWithContext(paramsText, cl)
        case _ =>
          ScalaPsiElementFactory.createParamClausesWithContext(paramsText, named, nameId)
      }
      val result = usage.paramClauses match {
        case Some(p) => p.replace(newClauses)
        case None => nameId.getParent.addAfter(newClauses, nameId)
      }
      ScalaPsiUtil.adjustTypes(result)
    }

    inner(usage.namedElement)
  }
  
  protected def handleUsageArguments(change: ChangeInfo, usage: UsageInfo): Unit = {
    usage match {
      case c: ConstructorUsageInfo => handleConstructorUsageArguments(change, c)
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
          val tupleText = arguments(change, usage).mkString("(", ", ", ")")
          val newTuple = ScalaPsiElementFactory.createExpressionWithContextFromText(tupleText, infix, t)
          t.replaceExpression(newTuple, removeParenthesis = false)
        case _ =>
          val methodCall = ScalaPsiElementFactory.createEquivMethodCall(infix)
          val argList = createArgList(change, usage)
          methodCall.args.replace(argList)
          infix.replaceExpression(methodCall, removeParenthesis = true)
      }
    } else {
      val argText = arguments(change, usage).headOption match {
        case Some(text) if text.trim.isEmpty => "()"
        case Some(text) => text
        case None => "()"
      }
      val expr = ScalaPsiElementFactory.createExpressionWithContextFromText(argText, infix, infix.getArgExpr)
      infix.getArgExpr.replaceExpression(expr, removeParenthesis = true)
    }
  }

  def handleConstructorUsageArguments(change: ChangeInfo, usage: ConstructorUsageInfo): Unit = {
    val constr = usage.constr
    val argList = createArgList(change, usage)
    constr.args match {
      case Some(a) => a.replace(argList)
      case None => constr.addAfter(argList, constr.typeElement)
    }
  }

  protected def handleRefUsageArguments(change: ChangeInfo, usage: RefExpressionUsage): Unit = {
    if (change.getNewParameters.isEmpty) return

    val ref = usage.refExpr
    val text = ref.getText + arguments(change, usage).mkString("(", ", ", ")")
    val call = ScalaPsiElementFactory.createExpressionWithContextFromText(text, ref.getContext, ref)
    ref.replaceExpression(call, removeParenthesis = true)
  }

  protected def handlePostfixUsage(change: ChangeInfo, usage: PostfixExprUsageInfo): Unit = {
    if (change.getNewParameters.isEmpty) return

    val postfix = usage.postfix
    val qualRef = ScalaPsiElementFactory.createEquivQualifiedReference(postfix)
    val argList = createArgList(change, usage)
    val text = qualRef.getText + argList.getText
    val call = ScalaPsiElementFactory.createExpressionWithContextFromText(text, postfix.getContext, postfix)
    postfix.replaceExpression(call, removeParenthesis = true)
  }

  protected def handleMethodCallUsagesArguments(change: ChangeInfo, usage: MethodCallUsageInfo): Unit = {
    val call = usage.call
    val argList = createArgList(change, usage)
    call.args.replace(argList)
  }

  private def createArgList(change: ChangeInfo, methodUsage: MethodUsageInfo): ScArgumentExprList = {
    val argText = arguments(change, methodUsage).mkString("(", ", ", ")")
    ScalaPsiElementFactory.createExpressionFromText(s"foo$argText", methodUsage.expr.getManager)
            .children.collectFirst{case al: ScArgumentExprList => al}.get
  }

  private def arguments(change: ChangeInfo, methodUsage: MethodUsageInfo): Seq[String] = {
    if (change.getNewParameters.length == 0) return Seq.empty

    val oldArgsInfo = methodUsage.argsInfo

    nonVarargArgs(change, oldArgsInfo) ++: varargsExprs(change, oldArgsInfo, methodUsage.expr)
  }

  private def newArgumentExpression(argsInfo: OldArgsInfo,
                                    newParam: ParameterInfo,
                                    manager: PsiManager,
                                    addDefaultArg: Boolean,
                                    named: Boolean): Option[String] = {

    val oldIdx = newParam.getOldIndex

    if (oldIdx < 0 && addDefaultArg) return None

    val default = newParam.getDefaultValue

    val withoutName =
      if (oldIdx < 0) {
        if (!default.isEmpty) default else " "
      }
      else {
        argsInfo.byOldParameterIndex.get(oldIdx) match {
          case None => return None
          case Some(seq) if seq.size > 1 => return None
          case Some(Seq(assignStmt: ScAssignStmt)) => return Some(assignStmt.getText)
          case Some(Seq(expr)) => expr.getText
        }
      }
    val argText = if (named) s"${newParam.getName} = $withoutName" else withoutName
    Some(argText)
  }

  private def replaceNameId(elem: PsiElement, newName: String) {
    elem match {
      case scRef: ScReferenceElement =>
        val newId = ScalaPsiElementFactory.createIdentifier(newName, scRef.getManager).getPsi
        scRef.nameId.replace(newId)
      case jRef: PsiReferenceExpression =>
        jRef.getReferenceNameElement match {
          case nameId: PsiIdentifier =>
            val factory: PsiElementFactory = JavaPsiFacade.getInstance(jRef.getProject).getElementFactory
            val newNameIdentifier: PsiIdentifier = factory.createIdentifier(newName)
            nameId.replace(newNameIdentifier)
          case _ =>
        }
      case _ =>
        elem.replace(ScalaPsiElementFactory.createIdentifier(newName, elem.getManager).getPsi)
    }
  }

  private def parameterListText(change: ChangeInfo, usage: ScalaNamedElementUsageInfo): String = {
    def paramType(paramInfo: ParameterInfo) = {
      val method = change.getMethod
      paramInfo match {
        case sInfo: ScalaParameterInfo =>
          val text = UsageUtil.substitutor(usage).subst(sInfo.scType).canonicalText
          val `=> ` = if (sInfo.isByName) ScalaPsiUtil.functionArrow(method.getProject) + " " else ""
          val `*` = if (sInfo.isRepeatedParameter) "*" else ""
          `=> ` + text + `*`
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
      if (oldIdx >= 0) usage.defaultValues(oldIdx)
      else change match {
        case sc: ScalaChangeInfo if sc.isAddDefaultArgs =>
          paramInfo.getDefaultValue match {
            case "" | null => Some(" ")
            case s => Some(s)
          }
        case _ => None
      }
    }
    def newParamName(p: ParameterInfo) = {
      val oldIdx = p.getOldIndex
      change match {
        case jc: JavaChangeInfo if oldIdx >= 0 =>
          val oldNameOfChanged = jc.getOldParameterNames()(oldIdx)
          val oldNameOfCurrent = usage.parameters(oldIdx).name
          if (oldNameOfChanged != oldNameOfCurrent) oldNameOfCurrent
          else p.getName
        case _ => p.getName
      }
    }

    val paramInfos = change.getNewParameters.toSeq
    val project = change.getMethod.getProject
    val params = paramInfos.map { p =>
      val typedName = ScalaExtractMethodUtils.typedName(newParamName(p), paramType(p), project, byName = false)
      val default = scalaDefaultValue(p).fold("")(" = " + _)
      typedName + default
    }
    params.mkString("(", ", ", ")")
  }

  private def isRepeated(p: ParameterInfo) = p match {
    case p: ScalaParameterInfo => p.isRepeatedParameter
    case p: JavaParameterInfo => p.isVarargType
    case _ => false
  }

  private def nonVarargArgs(change: ChangeInfo, oldArgsInfo: OldArgsInfo): Seq[String] = {

    val isAddDefault = change match {
      case c: ScalaChangeInfo => c.isAddDefaultArgs
      case c: JavaChangeInfo => c.isGenerateDelegate
      case _ => true
    }

    val manager = change.getMethod.getManager
    var needNamed = false
    val buffer = new ListBuffer[String]
    for {
      (param, idx) <- change.getNewParameters.toSeq.zipWithIndex
      if !isRepeated(param)
    } {
      newArgumentExpression(oldArgsInfo, param, manager, isAddDefault, needNamed) match {
        case Some(text) =>
          buffer += text
          if (text.contains("=") && idx > buffer.size - 1) needNamed = true
        case None => needNamed = true
      }
    }
    buffer.toSeq
  }

  private def varargsExprs(changeInfo: ChangeInfo, argsInfo: OldArgsInfo, context: PsiElement): Seq[String] = {
    val parameters = changeInfo.getNewParameters

    val param = parameters.last
    param match {
      case s: ScalaParameterInfo if s.isRepeatedParameter =>
      case j: JavaParameterInfo if j.isVarargType =>
      case _ => return Seq.empty
    }
    val oldIndex = param.getOldIndex
    changeInfo match {
      case jChangeInfo: JavaChangeInfo =>
        if (oldIndex < 0) {
          val text = param.getDefaultValue
          if (text != "") Seq(text)
          else Seq.empty
        }
        else {
          val (argExprs, wasNamed) = argsInfo.byOldParameterIndex.get(oldIndex) match {
            case Some(Seq(ScAssignStmt(_, Some(expr)))) => (Seq(expr), true)
            case Some(seq) => (seq, false)
            case _ => return Seq.empty
          }
          if (jChangeInfo.isArrayToVarargs) {
            argExprs match {
              case Seq(ScMethodCall(ElementText("Array"), arrayArgs)) => arrayArgs.map(_.getText)
              case Seq(expr) =>
                val typedText = ScalaExtractMethodUtils.typedName(expr.getText, "_*", expr.getProject, byName = false)
                val naming = if (wasNamed) param.getName + " = " else ""
                val text = naming + typedText
                Seq(text)
            }
          }
          else argExprs.map(_.getText)
        }
      case _ => Seq.empty
    }
  }
}
