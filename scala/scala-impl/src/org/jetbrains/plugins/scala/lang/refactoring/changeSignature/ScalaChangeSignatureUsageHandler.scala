package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.refactoring.changeSignature.{ChangeInfo, JavaChangeInfo, JavaParameterInfo, ParameterInfo}
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.scala.codeInsight.intention.types.{AddOnlyStrategy, AddOrRemoveStrategy}
import org.jetbrains.plugins.scala.extensions.{ChildOf, ElementText, ObjectExt, PsiElementExt, PsiTypeExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, JavaArrayType}
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiUtil, TypeAdjuster}
import org.jetbrains.plugins.scala.lang.refactoring._
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.changeInfo.ScalaChangeInfo
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.ScalaExtractMethodUtils
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.refactoring.rename.ScalaRenameUtil
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.mutable

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
      case ChildOf(ref: ScReference) if ScalaRenameUtil.isAliased(ref) =>
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

    ScalaChangeSignatureUsageHandler.changeVisibility(member, visibility)
  }

  protected def handleReturnTypeChange(change: ChangeInfo, usage: ScalaNamedElementUsageInfo): Unit = {

    def addType(element: ScNamedElement, oldTypeElem: Option[ScTypeElement], substType: ScType): Unit = {
      oldTypeElem match {
        case Some(te) =>
          val replaced = te.replace(createTypeElementFromText(substType.canonicalCodeText)(element.getManager))
          TypeAdjuster.markToAdjust(replaced)
        case None =>
          val (context, anchor) = ScalaPsiUtil.nameContext(element) match {
            case f: ScFunction => (f, f.paramClauses)
            case p: ScPatternDefinition => (p, p.pList)
            case v: ScVariableDefinition => (v, v.pList)
            case cp: ScClassParameter => (cp.getParent, cp)
            case ctx => (ctx, ctx.getLastChild)
          }

          new AddOnlyStrategy().addTypeAnnotation(substType, context, anchor)
      }
    }

    val element = usage.namedElement

    val oldTypeElem = element match {
      case fun: ScFunction => fun.returnTypeElement
      case ScalaPsiUtil.inNameContext(pd: ScPatternDefinition) => pd.typeElement
      case ScalaPsiUtil.inNameContext(vd: ScVariableDefinition) => vd.typeElement
      case cp: ScClassParameter => cp.typeElement
      case _ => None
    }

    val addTypeAnnotationOption = change match {
      case scalaInfo: ScalaChangeInfo => scalaInfo.addTypeAnnotation
      case _ => Some(true)
    }

    UsageUtil.returnType(change, usage).foreach { substType =>

      if (!change.isReturnTypeChanged) {
        addTypeAnnotationOption.foreach { addTypeAnnotation =>
          if (addTypeAnnotation) {
            if (oldTypeElem.isEmpty) {
              addType(element, None, substType)
            }
          } else {
            oldTypeElem.foreach(AddOrRemoveStrategy.removeTypeAnnotation)
          }
        }
      } else {
        addType(element, oldTypeElem, substType)
      }
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

    val expr = usage.expr

    val paramTypes = expr.`type`() match {
      case Right(FunctionType(_, pTypes)) => pTypes
      case _ => Seq.empty
    }
    val (names, exprText) = expr match {
      case inv: MethodInvocation =>
        var paramsBuf = Seq[String]()
        for {
          (arg, param) <- inv.matchedParameters.sortBy(_._2.index)
          if ScUnderScoreSectionUtil.isUnderscore(arg)
        } {
          val paramName =
            if (param.name.nonEmpty) param.name
            else param.nameInCode match {
              case Some(n) => n
              case None => NameSuggester.suggestNamesByType(param.paramType).head
            }
          paramsBuf = paramsBuf :+ paramName
          arg.replaceExpression(createExpressionFromText(paramName)(arg.getManager), removeParenthesis = true)
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
            ScalaExtractMethodUtils.typedName(name, tpe.canonicalCodeText)(expr.getProject)
        }
      else names
    val clause = params.mkString("(", ", ", ")")
    val newFunExprText = s"$clause => $exprText"
    val replaced = expr.replaceExpression(createExpressionFromText(newFunExprText)(expr.getManager), removeParenthesis = true) match {
      case fn: ScFunctionExpr => fn
      case ScParenthesisedExpr(fn: ScFunctionExpr) => fn
      case _ => return
    }
    TypeAdjuster.markToAdjust(replaced)
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

    val named = usage.namedElement

    val keywordToChange = named match {
      case _: ScFunction | _: ScClass => None
      case ScalaPsiUtil.inNameContext(pd: ScPatternDefinition) if pd.isSimple => Some(pd.keywordToken)
      case ScalaPsiUtil.inNameContext(vd: ScVariableDefinition) if vd.isSimple => Some(vd.keywordToken)
      case _ => return
    }
    keywordToChange.foreach { kw =>
      val defKeyword = createMethodFromText("def foo {}")(named.getManager).children.find(_.textMatches("def")).get
      if (change.getNewParameters.nonEmpty) kw.replace(defKeyword)
    }

    val paramsText = parameterListText(change, usage)
    val nameId = named.nameId
    val newClauses = named match {
      case cl: ScClass =>
        createClassParamClausesWithContext(paramsText, cl)
      case _ =>
        createParamClausesWithContext(paramsText, named, nameId)
    }
    val result = usage.paramClauses match {
      case Some(p) => p.replace(newClauses)
      case None => nameId.getParent.addAfter(newClauses, nameId)
    }
    TypeAdjuster.markToAdjust(result)
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
    val ScInfixExpr.withAssoc(ElementText(qualText), operation, argument) = infix

    if (change.getNewParameters.length != 1) {
      argument match {
        case t: ScTuple if !hasSeveralClauses(change) =>
          val tupleText = argsText(change, usage)
          val newTuple = createExpressionWithContextFromText(tupleText, infix, t)
          t.replaceExpression(newTuple, removeParenthesis = false)
        case _ =>
          val newCallText = s"$qualText.${operation.refName}${argsText(change, usage)}"
          val methodCall = createExpressionWithContextFromText(newCallText, infix.getContext, infix)
          infix.replaceExpression(methodCall, removeParenthesis = true)
      }
    } else {
      val argText = arguments(change, usage).headOption match {
        case Some(Seq(text)) if text.trim.isEmpty => "()"
        case Some(Seq(text)) => text
        case _ => "()"
      }
      val expr = createExpressionWithContextFromText(argText, infix, argument)
      argument.replaceExpression(expr, removeParenthesis = true)
    }
  }

  def handleConstructorUsageArguments(change: ChangeInfo, usage: ConstructorUsageInfo): Unit = {
    val constr = usage.constrInvocation
    val typeElem = constr.typeElement
    val text = typeElem.getText + argsText(change, usage)
    val newConstr = createConstructorFromText(text, constr.getContext, constr)

    constr.replace(newConstr)
  }

  protected def handleRefUsageArguments(change: ChangeInfo, usage: RefExpressionUsage): Unit = {
    if (change.getNewParameters.isEmpty) return

    val ref = usage.refExpr
    val text = ref.getText + argsText(change, usage)
    val call = createExpressionWithContextFromText(text, ref.getContext, ref)
    ref.replaceExpression(call, removeParenthesis = true)
  }

  protected def handlePostfixUsage(change: ChangeInfo, usage: PostfixExprUsageInfo): Unit = {
    if (change.getNewParameters.isEmpty) return

    val postfix = usage.postfix
    val qualRef = createEquivQualifiedReference(postfix)
    val text = qualRef.getText + argsText(change, usage)
    val call = createExpressionWithContextFromText(text, postfix.getContext, postfix)
    postfix.replaceExpression(call, removeParenthesis = true)
  }

  protected def handleMethodCallUsagesArguments(change: ChangeInfo, usage: MethodCallUsageInfo): Unit = {
    val call = usage.call
    val newText = usage.ref.getText + argsText(change, usage)
    val newCall = createExpressionWithContextFromText(newText, call.getContext, call)
    call.replace(newCall)
  }

  private def arguments(change: ChangeInfo, methodUsage: MethodUsageInfo): Seq[Seq[String]] = {
    if (change.getNewParameters.isEmpty) return Seq.empty
    val isAddDefault = change match {
      case c: ScalaChangeInfo => c.isAddDefaultArgs
      case c: JavaChangeInfo => c.isGenerateDelegate
      case _ => true
    }
    val manager = change.getMethod.getManager
    val oldArgsInfo = methodUsage.argsInfo

    def nonVarargArgs(clause: Seq[ParameterInfo]): Seq[String] = {
      var needNamed = false
      val builder = mutable.ListBuffer.empty[String]
      for {
        (param, idx) <- clause.zipWithIndex
        if !isRepeated(param)
      } {
        newArgumentExpression(oldArgsInfo, param, manager, isAddDefault, needNamed) match {
          case Some(text) =>
            builder += text
            if (text.contains("=") && idx >= builder.size) needNamed = true
          case None => needNamed = true
        }
      }
      builder.result()
    }

    def varargsExprs(clause: Seq[ParameterInfo]): Seq[String] = {
      val param = clause.last
      param match {
        case s: ScalaParameterInfo if s.isRepeatedParameter =>
        case j: JavaParameterInfo if j.isVarargType =>
        case _ => return Seq.empty
      }
      val oldIndex = param.getOldIndex
      change match {
        case jChangeInfo: JavaChangeInfo =>
          if (oldIndex < 0) {
            val text = param.getDefaultValue
            if (text != "") Seq(text)
            else Seq.empty
          }
          else {
            val (argExprs, wasNamed) = oldArgsInfo.byOldParameterIndex.get(oldIndex) match {
              case Some(Seq(ScAssignment(_, Some(expr)))) => (Seq(expr), true)
              case Some(seq) => (seq, false)
              case _ => return Seq.empty
            }
            if (jChangeInfo.isArrayToVarargs) {
              argExprs match {
                case Seq(ScMethodCall(ElementText("Array"), arrayArgs)) => arrayArgs.map(_.getText)
                case Seq(expr) =>
                  val typedText = ScalaExtractMethodUtils.typedName(expr.getText, "_*")(expr.getProject)
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

    def toArgs(clause: Seq[ParameterInfo]): Seq[String] = nonVarargArgs(clause) ++: varargsExprs(clause)

    change match {
      case sc: ScalaChangeInfo => sc.newParams.filter(_.nonEmpty).map(toArgs)
      case _ => Seq(change.getNewParameters.toSeq).map(toArgs)
    }
  }

  def argsText(change: ChangeInfo, methodUsage: MethodUsageInfo): String = {
    val args = arguments(change, methodUsage)
    if (args.isEmpty && !methodUsage.is[RefExpressionUsage])
      "()"
    else
      args.map(_.mkString("(", ", ", ")")).mkString
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
        if (default != null && default.nonEmpty) default else ""
      }
      else {
        argsInfo.byOldParameterIndex.get(oldIdx) match {
          case Some(Seq(assignStmt: ScAssignment)) => return Some(assignStmt.getText)
          case Some(Seq(expr)) => expr.getText
          case _ => return None
        }
      }
    val argText = if (named) s"${newParam.getName} = $withoutName" else withoutName
    Some(argText)
  }

  private def replaceNameId(elem: PsiElement, newName: String): Unit = {
    implicit val ctx: ProjectContext = elem
    elem match {
      case scRef: ScReference =>
        val newId = createIdentifier(newName).getPsi
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
        elem.replace(createIdentifier(newName).getPsi)
    }
  }

  private def parameterListText(change: ChangeInfo, usage: ScalaNamedElementUsageInfo): String = {
    implicit val project: Project = change.getMethod.getProject

    def paramType(paramInfo: ParameterInfo) = {
      val method = change.getMethod
      paramInfo match {
        case sInfo: ScalaParameterInfo =>
          val text = UsageUtil.substitutor(usage)(sInfo.scType).canonicalCodeText
          val `=> ` = if (sInfo.isByName) ScalaPsiUtil.functionArrow + " " else ""
          val `*` = if (sInfo.isRepeatedParameter) "*" else ""
          `=> `+ text + `*`
        case jInfo: JavaParameterInfo =>
          val javaType = jInfo.createType(method, method.getManager)
          val scType = UsageUtil.substitutor(usage)(javaType.toScType())
          (scType, javaType) match {
            case (JavaArrayType(argument), _: PsiEllipsisType) => argument.canonicalCodeText + "*"
            case _ => scType.canonicalCodeText
          }
        case info => info.getTypeText
      }
    }
    def scalaDefaultValue(paramInfo: ParameterInfo): Option[String] = {
      val oldIdx = paramInfo.getOldIndex
      if (oldIdx >= 0) usage.defaultValues(oldIdx)
      else change match {
        case sc: ScalaChangeInfo if !sc.function.isConstructor && sc.function != usage.namedElement => None
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

    def paramText(p: ParameterInfo) = {
      val typedName = ScalaExtractMethodUtils.typedName(newParamName(p), paramType(p))
      val default = scalaDefaultValue(p).fold("")(" = " + _)
      val keywordsAndAnnots = p match {
        case spi: ScalaParameterInfo => spi.keywordsAndAnnotations
        case _ => ""
      }
      keywordsAndAnnots + typedName + default
    }

    change match {
      case sc: ScalaChangeInfo =>
        sc.newParams.map(cl => cl.map(paramText).mkString("(", ", ", ")")).mkString
      case _ => change.getNewParameters.toSeq.map(paramText).mkString("(", ", ", ")")
    }
  }

  private def hasSeveralClauses(change: ChangeInfo): Boolean = {
    change match {
      case sc: ScalaChangeInfo => sc.newParams.size > 1
      case _ => false
    }
  }

  private def isRepeated(p: ParameterInfo) = p match {
    case p: ScalaParameterInfo => p.isRepeatedParameter
    case p: JavaParameterInfo => p.isVarargType
    case _ => false
  }
}

private object ScalaChangeSignatureUsageHandler {

  def changeVisibility(member: ScModifierListOwner,
                       @PsiModifier.ModifierConstant
                       newVisibility: String): Unit = {
    implicit val projectContext: ProjectContext = member.projectContext
    val modifierList = member.getModifierList
    newVisibility match {
      case "" | PsiModifier.PUBLIC | PsiModifier.PACKAGE_LOCAL =>
        modifierList.accessModifier.foreach(_.delete())
      case _ =>
        val newElem = createModifierFromText(newVisibility)
        modifierList.accessModifier match {
          case Some(mod) =>
            mod.replace(newElem)
          case None =>
            if (modifierList.children.isEmpty) {
              modifierList.add(newElem)
            } else {
              val mod = modifierList.getFirstChild
              modifierList.addBefore(newElem, mod)
              modifierList.addBefore(createWhitespace, mod)
            }
        }
    }
  }
}
