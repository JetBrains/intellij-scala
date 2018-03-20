package org.jetbrains.plugins.scala
package lang
package parameterInfo

import java.awt.Color

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.completion.JavaCompletionUtil
import com.intellij.codeInsight.hint.ShowParameterInfoHandler
import com.intellij.codeInsight.lookup.{LookupElement, LookupItem}
import com.intellij.lang.parameterInfo._
import com.intellij.psi._
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.containers.hash.HashSet
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parameterInfo.ScalaFunctionParameterInfoHandler.AnnotationParameters
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScTypeElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeParametersOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.processor.ImplicitCompletionProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult, StdKinds}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec
import scala.collection.Seq
import scala.collection.mutable.ArrayBuffer

/**
 * User: Alexander Podkhalyuzin
 * Date: 18.01.2009
 */

class ScalaFunctionParameterInfoHandler extends ParameterInfoHandlerWithTabActionSupport[PsiElement, Any, ScExpression] {
  def getArgListStopSearchClasses: java.util.Set[_ <: Class[_]] = {
    java.util.Collections.singleton(classOf[PsiMethod])
  }

  override def getParameterCloseChars: String = "{},);\n"

  def couldShowInLookup: Boolean = true

  def getActualParameterDelimiterType: IElementType = ScalaTokenTypes.tCOMMA

  def getActualParameters(elem: PsiElement): Array[ScExpression] = {
    elem match {
      case argExprList: ScArgumentExprList =>
        argExprList.exprs.toArray
      case _: ScUnitExpr => Array.empty
      case p: ScParenthesisedExpr => p.innerElement.toArray
      case t: ScTuple => t.exprs.toArray
      case e: ScExpression => Array(e)
      case _ => Array.empty
    }
  }

  def getArgumentListClass: Class[PsiElement] = classOf[PsiElement]

  def getActualParametersRBraceType: IElementType = ScalaTokenTypes.tRBRACE

  def getArgumentListAllowedParentClasses: java.util.Set[Class[_]] = {
    val set = new HashSet[Class[_]]()
    set.add(classOf[ScMethodCall])
    set.add(classOf[ScConstructor])
    set.add(classOf[ScSelfInvocation])
    set.add(classOf[ScInfixExpr])
    set
  }

  def findElementForParameterInfo(context: CreateParameterInfoContext): PsiElement = {
    findCall(context)
  }

  def findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): PsiElement = {
    findCall(context)
  }

  override def getParametersForDocumentation(p: Any, context: ParameterInfoContext): Array[Object] = {
    p match {
      case x: ScFunction =>
        x.parameters.toArray
      case _ => ArrayUtil.EMPTY_OBJECT_ARRAY
    }
  }

  def showParameterInfo(element: PsiElement, context: CreateParameterInfoContext) {
    context.showHint(element, element.getTextRange.getStartOffset, this)
  }

  def getParametersForLookup(item: LookupElement, context: ParameterInfoContext): Array[Object] = {
    if (!item.isInstanceOf[LookupItem[_]]) return null
    val allElements = JavaCompletionUtil.getAllPsiElements(item.asInstanceOf[LookupItem[_]])

    if (allElements != null &&
        allElements.size > 0 &&
        allElements.get(0).isInstanceOf[PsiMethod]) {
      return allElements.toArray(new Array[Object](allElements.size))
    }
    null
  }

  def updateParameterInfo(o: PsiElement, context: UpdateParameterInfoContext) {
    if (context.getParameterOwner != o) context.removeHint()
    val offset = context.getOffset
    var child = o.getNode.getFirstChildNode
    var i = 0
    while (child != null && child.getStartOffset < offset) {
      if (child.getElementType == ScalaTokenTypes.tCOMMA) i = i + 1
      child = child.getTreeNext
    }
    context.setCurrentParameter(i)
  }


  def updateUI(p: Any, context: ParameterInfoUIContext) {
    if (context == null || context.getParameterOwner == null || !context.getParameterOwner.isValid) return
    context.getParameterOwner match {
      case args: PsiElement =>
        implicit val project: ProjectContext = args.projectContext
        val color: Color = context.getDefaultParameterColor
        val index = context.getCurrentParameterIndex
        val buffer: StringBuilder = new StringBuilder("")
        var isGrey = false
        //todo: var isGreen = true
        var namedMode = false
        def paramText(param: ScParameter, subst: ScSubstitutor) = {
          ScalaDocumentationProvider.parseParameter(param, escape = false)(subst.subst(_).presentableText)
        }
        def applyToParameters(parameters: Seq[(Parameter, String)], subst: ScSubstitutor, canBeNaming: Boolean,
                              isImplicit: Boolean = false) {
          if (parameters.nonEmpty) {
            var k = 0
            val exprs: Seq[ScExpression] = getActualParameters(args)
            if (isImplicit) buffer.append("implicit ")
            val used = new Array[Boolean](parameters.length)
            while (k < parameters.length) {
              val namedPrefix = "["
              val namedPostfix = "]"

              def appendFirst(useGrey: Boolean = false) {
                val getIt = used.indexOf(false)
                used(getIt) = true
                if (namedMode) buffer.append(namedPrefix)
                val param: (Parameter, String) = parameters(getIt)

                buffer.append(param._2)
                if (namedMode) buffer.append(namedPostfix)
              }
              def doNoNamed(expr: ScExpression) {
                if (namedMode) {
                  isGrey = true
                  appendFirst()
                } else {
                  val exprType = expr.`type`().getOrNothing
                  val getIt = used.indexOf(false)
                  used(getIt) = true
                  val param: (Parameter, String) = parameters(getIt)
                  val paramType = param._1.paramType
                  if (!exprType.conforms(paramType)) isGrey = true
                  buffer.append(param._2)
                }
              }
              if (k == index || (k == parameters.length - 1 && index >= parameters.length &&
                      parameters.last._1.isRepeated)) {
                buffer.append("<b>")
              }
              if (k < index && !isGrey) {
                //slow checking
                if (k >= exprs.length) { //shouldn't be
                  appendFirst(useGrey = true)
                  isGrey = true
                } else {
                  exprs(k) match {
                    case assign@NamedAssignStmt(name) =>
                      val ind = parameters.indexWhere(param => ScalaNamesUtil.equivalent(param._1.name, name))
                      if (ind == -1 || used(ind)) {
                        doNoNamed(assign)
                      } else {
                        if (k != ind) namedMode = true
                        used(ind) = true
                        val param: (Parameter, String) = parameters(ind)
                        if (namedMode) buffer.append(namedPrefix)
                        buffer.append(param._2)
                        if (namedMode) buffer.append(namedPostfix)
                        assign.getRExpression match {
                          case Some(expr: ScExpression) =>
                            for (exprType <- expr.`type`()) {
                              val paramType = param._1.paramType
                              if (!exprType.conforms(paramType)) isGrey = true
                            }
                          case _ => isGrey = true
                        }
                      }
                    case expr: ScExpression =>
                      doNoNamed(expr)
                  }
                }
              } else {
                //fast checking
                if (k >= exprs.length) {
                  appendFirst()
                } else {
                  exprs(k) match {
                    case NamedAssignStmt(name) =>
                      val ind = parameters.indexWhere(param => ScalaNamesUtil.equivalent(param._1.name, name))
                      if (ind == -1 || used(ind)) {
                        appendFirst()
                      } else {
                        if (k != ind) namedMode = true
                        used(ind) = true
                        if (namedMode) buffer.append(namedPrefix)
                        buffer.append(parameters(ind)._2)
                        if (namedMode) buffer.append(namedPostfix)
                      }
                    case _ => appendFirst()
                  }
                }
              }
              if (k == index || (k == parameters.length - 1 && index >= parameters.length &&
                      parameters.last._1.isRepeated)) {
                buffer.append("</b>")
              }
              k = k + 1
              if (k != parameters.length) buffer.append(", ")
            }
            if (!isGrey && exprs.length > parameters.length && index >= parameters.length) {
              if (!namedMode && parameters.last._1.isRepeated) {
                val paramType = parameters.last._1.paramType
                while (!isGrey && k < exprs.length.min(index)) {
                  if (k < index) {
                    for (exprType <- exprs(k).`type`()) {
                    if (!exprType.conforms(paramType)) isGrey = true
                    }
                  }
                  k = k + 1
                }
              } else isGrey = true
            }
          } else buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
        }
        p match {
          case x: String if x == "" =>
            buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
          case (a: AnnotationParameters, _: Int) =>
            val seq = a.seq
            if (seq.isEmpty) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
            else {
              val paramsSeq: Seq[(Parameter, String)] = seq.zipWithIndex.map {
                case ((name, tp, value), paramIndex) =>
                  val valueText = Option(value).map(_.getText)
                    .map(" = " + _)
                    .getOrElse("")

                  (new Parameter(name, None, tp, tp, value != null, false, false, paramIndex),
                    s"$name: ${tp.presentableText}$valueText")
              }
              applyToParameters(paramsSeq, ScSubstitutor.empty, canBeNaming = true, isImplicit = false)
            }
          case (sign: PhysicalSignature, i: Int) => //i  can be -1 (it's update method)
            val subst = sign.substitutor
            sign.method match {
              case method: ScFunction =>
                val clauses = method.effectiveParameterClauses
                if (clauses.length <= i || (i == -1 && clauses.isEmpty)) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
                else {
                  val clause: ScParameterClause = if (i >= 0) clauses(i) else clauses.head
                  val length = clause.effectiveParameters.length
                  val parameters: Seq[ScParameter] = if (i != -1) clause.effectiveParameters else clause.effectiveParameters.take(length - 1)
                  applyToParameters(parameters.map(param =>
                    (Parameter(param), paramText(param, subst))), subst, canBeNaming = true, isImplicit = clause.isImplicit)
                }
              case method: FakePsiMethod =>
                if (method.params.length == 0) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
                else {
                  buffer.append(method.params.
                          map((param: Parameter) => {
                    val buffer: StringBuilder = new StringBuilder("")
                    val paramType = param.paramType
                    val name = param.name
                    if (name != "") {
                      buffer.append(name)
                      buffer.append(": ")
                    }
                            buffer.append(paramType.presentableText)
                    if (param.isRepeated) buffer.append("*")

                    if (param.isDefault) buffer.append(" = _")

                    val isBold = if (method.params.indexOf(param) == index || (param.isRepeated && method.params.indexOf(param) <= index)) true
                    else {
                      //todo: check type
                      false
                    }
                    val paramText = buffer.toString()
                    if (isBold) "<b>" + paramText + "</b>" else paramText
                  }).mkString(", "))
                }
              case method: PsiMethod =>
                val p = method.getParameterList
                if (p.getParameters.isEmpty) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
                else {
                  buffer.append(p.getParameters.
                          map((param: PsiParameter) => {
                    val buffer: StringBuilder = new StringBuilder("")
                    val list = param.getModifierList
                    if (list == null) return
                    val lastSize = buffer.length
                    for (a <- list.getAnnotations) {
                      if (lastSize != buffer.length) buffer.append(" ")
                      val element = a.getNameReferenceElement
                      if (element != null) buffer.append("@").append(element.getText)
                    }
                    if (lastSize != buffer.length) buffer.append(" ")

                    val name = param.name
                    if (name != null) {
                      buffer.append(name)
                    }
                    buffer.append(": ")
                            buffer.append(subst.subst(param.paramType()).presentableText)
                    if (param.isVarArgs) buffer.append("*")

                    val isBold = if (p.getParameters.indexOf(param) == index || (param.isVarArgs && p.getParameters.indexOf(param) <= index)) true
                    else {
                      //todo: check type
                      false
                    }
                    val paramText = buffer.toString()
                    if (isBold) "<b>" + paramText + "</b>" else paramText
                  }).mkString(", "))
                }
            }
          case (constructor: ScPrimaryConstructor, subst: ScSubstitutor, i: Int) if constructor.isValid =>
            val clauses = constructor.effectiveParameterClauses
            if (clauses.length <= i) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
            else {
              val clause: ScParameterClause = clauses(i)
              applyToParameters(clause.effectiveParameters.map(param =>
                (Parameter(param), paramText(param, subst))), subst, canBeNaming = true, isImplicit = clause.isImplicit)
            }
          case _ =>
        }
        val startOffset = buffer.indexOf("<b>")
        if (startOffset != -1) buffer.replace(startOffset, startOffset + 3, "")

        val endOffset = buffer.indexOf("</b>")
        if (endOffset != -1) buffer.replace(endOffset, endOffset + 4, "")

        if (buffer.toString != "")
          context.setupUIComponentPresentation(buffer.toString(), startOffset, endOffset, isGrey, false, false, color)
        else
          context.setUIComponentEnabled(false)
      case _ =>
    }
  }

  override def tracksParameterIndex: Boolean = true

  trait Invocation {
    def element: PsiElement
    def parent: PsiElement = element.getParent
    def invocationCount: Int
    def callGeneric: Option[ScGenericCall] = None
    def callReference: Option[ScReferenceExpression]
    def arguments: Seq[ScExpression]
  }

  object Invocation {
    private class CallInvocation(args: ScArgumentExprList) extends Invocation {
      override def element: PsiElement = args

      override def callGeneric: Option[ScGenericCall] = args.callGeneric

      override def invocationCount: Int = args.invocationCount

      override def callReference: Option[ScReferenceExpression] = args.callReference

      override def arguments: Seq[ScExpression] = args.exprs
    }
    private trait InfixInvocation extends Invocation {
      override def invocationCount: Int = 1

      override def callReference: Option[ScReferenceExpression] = {
        element.getParent match {
          case i: ScInfixExpr => Some(i.operation)
        }
      }
    }
    private class InfixExpressionInvocation(expr: ScExpression) extends InfixInvocation {
      override def element: PsiElement = expr

      override def arguments: Seq[ScExpression] = Seq(expr)
    }
    private class InfixTupleInvocation(tuple: ScTuple) extends InfixInvocation {
      override def element: PsiElement = tuple

      override def arguments: Seq[ScExpression] = tuple.exprs
    }
    private class InfixUnitInvocation(u: ScUnitExpr) extends InfixInvocation {
      override def element: PsiElement = u

      override def arguments: Seq[ScExpression] = Seq(u)
    }

    def getInvocation(elem: PsiElement): Option[Invocation] = {
      def create[T <: PsiElement](elem: T)
                                 (f: T => Invocation): Option[Invocation] =
        elem.getParent match {
          case ScInfixExpr.withAssoc(_, _, `elem`) => Some(f(elem))
          case _ => None
        }

      elem match {
        case args: ScArgumentExprList => Some(new CallInvocation(args))
        case t: ScTuple => create(t)(new InfixTupleInvocation(_))
        case u: ScUnitExpr => create(u)(new InfixUnitInvocation(_))
        case e: ScExpression => create(e)(new InfixExpressionInvocation(_))
        case _ => None
      }
    }
  }


  def elementsForParameterInfo(args: Invocation): Seq[Object] = {
    implicit val project: ProjectContext = args.element.projectContext
    args.parent match {
      case call: MethodInvocation =>
        val res: ArrayBuffer[Object] = new ArrayBuffer[Object]
        def collectResult() {
          val canBeUpdate = call.getParent match {
            case assignStmt: ScAssignStmt if call == assignStmt.getLExpression => true
            case notExpr if !notExpr.isInstanceOf[ScExpression] || notExpr.isInstanceOf[ScBlockExpr] => true
            case _ => false
          }
          val count = args.invocationCount
          val gen = args.callGeneric.getOrElse(null: ScGenericCall)
          def collectSubstitutor(element: PsiElement): ScSubstitutor = {
            if (gen == null) return ScSubstitutor.empty
            val typeParams = element match {
              case tpo: ScTypeParametersOwner => tpo.typeParameters.toArray
              case ptpo: PsiTypeParameterListOwner => ptpo.getTypeParameters
              case _ => return ScSubstitutor.empty
            }
            ScSubstitutor.bind(typeParams, gen.arguments)(_.calcType)
          }
          def collectForType(typez: ScType): Unit = {
            def process(functionName: String): Unit = {
              val i = if (functionName == "update") -1 else 0
              val processor = new ImplicitCompletionProcessor(StdKinds.refExprQualRef, call) {

                override protected val forName = Some(functionName)
              }
              processor.processType(typez, call)
              val variants: Array[ScalaResolveResult] = processor.candidates
              for {
                variant <- variants
                if !variant.getElement.isInstanceOf[PsiMember] ||
                  ResolveUtils.isAccessible(variant.getElement.asInstanceOf[PsiMember], call)
              } {
                variant match {
                  case ScalaResolveResult(method: ScFunction, subst: ScSubstitutor) =>
                    val signature: PhysicalSignature = new PhysicalSignature(method, subst.followed(collectSubstitutor(method)))
                    res += ((signature, i))
                    res ++= ScalaParameterInfoEnhancer.enhance(signature, args.arguments).map { (_, i) }
                  case _ =>
                }
              }
            }

            process("apply")
            if (canBeUpdate) process("update")
          }
          args.callReference match {
            case Some(ref: ScReferenceExpression) =>
              if (count > 1) {
                //todo: missed case with last implicit call
                ref.bind() match {
                  case Some(ScalaResolveResult(function: ScFunction, subst: ScSubstitutor)) if function.
                    effectiveParameterClauses.length >= count =>
                    res += ((new PhysicalSignature(function, subst.followed(collectSubstitutor(function))), count - 1))
                  case _ =>
                    for (typez <- call.getEffectiveInvokedExpr.`type`()) //todo: implicit conversions
                    {collectForType(typez)}
                }
              } else {
                val variants = {
                  val sameName = ref.getSameNameVariants
                  if (sameName.isEmpty) ref.multiResolveScala(false)
                  else sameName
                }
                for {
                  variant <- variants
                  if !variant.getElement.isInstanceOf[PsiMember] ||
                    ResolveUtils.isAccessible(variant.getElement.asInstanceOf[PsiMember], ref)
                } {
                  variant match {
                    //todo: Synthetic function
                    case ScalaResolveResult(method: PsiMethod, subst: ScSubstitutor) =>
                      val signature: PhysicalSignature = new PhysicalSignature(method, subst.followed(collectSubstitutor(method)))
                      res += ((signature, 0))
                      res ++= ScalaParameterInfoEnhancer.enhance(signature, args.arguments).map { (_, 0) }
                    case ScalaResolveResult(typed: ScTypedDefinition, subst: ScSubstitutor) =>
                      val typez = subst.subst(typed.`type`().getOrNothing) //todo: implicit conversions
                      collectForType(typez)
                    case _ =>
                  }
                }
              }
            case None =>
              call match {
                case call: ScMethodCall =>
                  for (typez <- call.getEffectiveInvokedExpr.`type`()) { //todo: implicit conversions
                    collectForType(typez)
                  }
              }
          }
        }
        collectResult()
        res
      case constr: ScConstructor =>
        val res: ArrayBuffer[Object] = new ArrayBuffer[Object]
        val typeElement = constr.typeElement
        val i = constr.arguments.indexOf(args.element)
        typeElement.calcType.extractClassType match {
          case Some((clazz: PsiClass, subst: ScSubstitutor)) =>
            clazz match {
              case clazz: ScClass =>
                clazz.constructor match {
                  case Some(constr: ScPrimaryConstructor) if i < constr.effectiveParameterClauses.length =>
                    typeElement match {
                      case gen: ScParameterizedTypeElement =>
                        val substitutor = ScSubstitutor.bind(clazz.typeParameters, gen.typeArgList.typeArgs)(_.calcType)
                        res += ((constr, substitutor.followed(subst), i))
                      case _ => res += ((constr, subst, i))
                    }
                  case Some(_) if i == 0 => res += ""
                  case None => res += ""
                  case _ =>
                }
                for (constr <- clazz.functions if !constr.isInstanceOf[ScPrimaryConstructor] &&
                  constr.isConstructor && ((constr.clauses match {
                  case Some(x) => x.clauses.length
                  case None => 1
                }) > i))
                  res += ((new PhysicalSignature(constr, subst), i))
              case clazz: PsiClass if clazz.isAnnotationType =>
                val resulting: (AnnotationParameters, Int) =
                  (AnnotationParameters(clazz.getMethods.toSeq.filter(_.isInstanceOf[PsiAnnotationMethod]).map(meth => (meth.name,
                    meth.getReturnType.toScType(),
                    meth.asInstanceOf[PsiAnnotationMethod].getDefaultValue))), i)
                res += resulting
              case clazz: PsiClass if !clazz.isInstanceOf[ScTypeDefinition] =>
                for (constructor <- clazz.getConstructors) {
                  typeElement match {
                    case gen: ScParameterizedTypeElement =>
                      val substitutor = ScSubstitutor.bind(clazz.getTypeParameters, gen.typeArgList.typeArgs)(_.calcType)
                      res += ((new PhysicalSignature(constructor, substitutor.followed(subst)), i))
                    case _ => res += ((new PhysicalSignature(constructor, subst), i))
                  }
                }
              case _ =>
            }
          case _ =>
        }
        res
      case self: ScSelfInvocation =>
        val res: ArrayBuffer[Object] = new ArrayBuffer[Object]
        val i = self.arguments.indexOf(args.element)

        self.parentOfType(classOf[ScClass]).foreach { clazz =>
          clazz.constructor match {
            case Some(constr: ScPrimaryConstructor) if i < constr.effectiveParameterClauses.length =>
              res += ((constr, ScSubstitutor.empty, i))
            case Some(_) if i == 0 => res += ""
            case None => res += ""
            case _ =>
          }

          for {
            constr <- clazz.functions
            if !constr.isInstanceOf[ScPrimaryConstructor] &&
              constr.isConstructor &&
              constr.clauses.map(_.clauses.length).getOrElse(1) > i
          } {
            if (!PsiTreeUtil.isAncestor(constr, self, true) &&
              constr.getTextRange.getStartOffset < self.getTextRange.getStartOffset) {
              res += ((new PhysicalSignature(constr, ScSubstitutor.empty), i))
            }
          }
        }
        res
    }
  }

  /**
   * Returns context's argument psi and fill context items
   * by appropriate PsiElements (in which we can resolve)
    *
   * @param context current context
   * @return context's argument expression
   */
  private def findCall(context: ParameterInfoContext): PsiElement = {
    val file = context.getFile
    val offset = context.getEditor.getCaretModel.getOffset
    val element = file.findElementAt(offset)
    if (element.isInstanceOf[PsiWhiteSpace])
    if (element == null) return null
    @tailrec
    def findArgs(elem: PsiElement): Option[Invocation] = {
      if (elem == null) return None
      val res = Invocation.getInvocation(elem)
      if (res.isDefined) return res
      findArgs(elem.getParent)
    }
    val argsOption: Option[Invocation] = findArgs(element)
    if (argsOption.isEmpty) return null
    val args = argsOption.get
    implicit val project: ProjectContext = file.projectContext
    context match {
      case context: CreateParameterInfoContext =>
        context.setItemsToShow(elementsForParameterInfo(args).toArray)
      case context: UpdateParameterInfoContext =>
        var el = element
        while (el.getParent != args.element) el = el.getParent
        var index = 1
        for (expr <- getActualParameters(args.element) if expr != el) index += 1
        context.setCurrentParameter(index)
        context.setHighlightedParameter(el)

        if (!equivalent(context.getObjectsToView, elementsForParameterInfo(args))) {
          context.removeHint()

          ShowParameterInfoHandler.invoke(project, context.getEditor, context.getFile, context.getOffset, null, false)
        }
      case _ =>
    }
    args.element
  }

  private def equivalent(seq1: Seq[AnyRef], seq2: Seq[AnyRef]): Boolean = {
    seq1.size == seq2.size && seq1.zip(seq2).forall(equivObjectsToView)
  }

  private def equivObjectsToView(tuple: (AnyRef, AnyRef)): Boolean = tuple match {
    case (s1: String, s2: String) =>
      s1 == s2
    case ((a1: AnnotationParameters, i1: Int), (a2: AnnotationParameters, i2: Int)) =>
      i1 == i2 && a1 == a2
    case ((sign1: PhysicalSignature, i1: Int), (sign2: PhysicalSignature, i2: Int)) =>
      i1 == i2 && sign1.method == sign2.method
    case ((pc1: ScPrimaryConstructor, _: ScSubstitutor, i1: Int), (pc2: ScPrimaryConstructor, _: ScSubstitutor, i2: Int)) =>
      i1 == i2 && pc1 == pc2
    case _ => false
  }
}

object ScalaFunctionParameterInfoHandler {
  case class AnnotationParameters(seq: Seq[(String, ScType, PsiAnnotationMemberValue)])
}