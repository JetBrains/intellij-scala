package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup._
import com.intellij.openapi.application.ApplicationManager
import com.intellij.patterns.PsiElementPattern.Capture
import com.intellij.patterns.{ElementPattern, PlatformPatterns, StandardPatterns}
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.completion.handlers.ScalaGenerateAnonymousFunctionInsertHandler
import org.jetbrains.plugins.scala.lang.completion.lookups.{ScalaChainLookupElement, ScalaKeywordLookupItem, ScalaLookupItem}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult, StdKinds}

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.09.2009
 */

class ScalaSmartCompletionContributor extends ScalaCompletionContributor {
  import org.jetbrains.plugins.scala.lang.completion.ScalaSmartCompletionContributor._

  private def acceptTypes(typez: Seq[ScType], variants: Array[Object], result: CompletionResultSet,
                          scope: GlobalSearchScope, secondCompletion: Boolean, completeThis: Boolean,
                          place: PsiElement, originalPlace: PsiElement) {
    implicit val projectContext = place.projectContext

    def isAccessible(el: ScalaLookupItem): Boolean = {
      ScalaPsiUtil.nameContext(el.element) match {
        case memb: ScMember =>
          ResolveUtils.isAccessible(memb, place, forCompletion = true)
        case _ => true
      }
    }

    if (typez.isEmpty || typez.forall(_ == Nothing)) return

    def applyVariant(variant: Object, checkForSecondCompletion: Boolean = false) {

      def handleVariant(scalaLookupItem: ScalaLookupItem, chainVariant: Boolean = false): Unit = {
        val elemToAdd = variant.asInstanceOf[LookupElement]
        if (isAccessible(scalaLookupItem) && !scalaLookupItem.isNamedParameterOrAssignment) {
          def checkType(_tp: ScType, _subst: ScSubstitutor, chainCompletion: Boolean, etaExpanded: Boolean = false): Boolean = {
            val tp = _subst.subst(_tp)
            var elementAdded = false
            val scType = scalaLookupItem.substitutor.subst(tp)
            if (!scType.equiv(Nothing) && typez.exists(scType conforms _)) {
              elementAdded = true
              if (etaExpanded) scalaLookupItem.etaExpanded = true
              result.addElement(elemToAdd)
            } else {
              typez.foreach {
                case ParameterizedType(tpe, Seq(arg)) if !elementAdded =>
                  tpe.extractClass match {
                    case Some(clazz) if clazz.qualifiedName == "scala.Option" || clazz.qualifiedName == "scala.Some" =>
                      if (!scType.equiv(Nothing) && scType.conforms(arg)) {
                        scalaLookupItem.someSmartCompletion = true
                        if (etaExpanded) scalaLookupItem.etaExpanded = true
                        result.addElement(elemToAdd)
                        elementAdded = true
                      }
                    case _ =>
                  }
                case _ =>
              }
            }
            if (!elementAdded && chainCompletion && secondCompletion) {
              val processor = new CompletionProcessor(StdKinds.refExprLastRef, place) {

                override protected def postProcess(result: ScalaResolveResult): Unit = {
                  if (!result.isNamedParameter) {
                    val newElem = result.getLookupElement().head
                      applyVariant(new ScalaChainLookupElement(scalaLookupItem, newElem))
                  }
                }
              }
              processor.processType(scalaLookupItem.substitutor.subst(_tp), place)
              processor.candidatesS
            }
            elementAdded
          }

          scalaLookupItem.element match {
            case clazz: PsiClass if ScalaCompletionUtil.isExcluded(clazz) =>
            case fun: ScSyntheticFunction =>
              val second = checkForSecondCompletion && fun.paramClauses.flatten.isEmpty
              checkType(fun.retType, ScSubstitutor.empty, second)
            case fun: ScFunction =>
              if (fun.containingClass != null && fun.containingClass.qualifiedName == "scala.Predef") {
                fun.name match {
                  case "implicitly" | "identity" | "locally" => return
                  case _ =>
                }
              }
              val infer = if (chainVariant) ScSubstitutor.empty else ScalaPsiUtil.inferMethodTypesArgs(fun, scalaLookupItem.substitutor)
              val second = checkForSecondCompletion &&
                fun.paramClauses.clauses.filterNot(_.isImplicit).flatMap(_.parameters).isEmpty
              val added = fun.returnType match {
                case Right(tp) => checkType(tp, infer, second)
                case _ => false
              }
              if (!added) {
                fun.`type`() match {
                  case Right(tp) => checkType(tp, infer, second, etaExpanded = true)
                  case _ =>
                }
              }
            case method: PsiMethod =>
              val second = checkForSecondCompletion && method.getParameterList.getParametersCount == 0
              val infer = if (chainVariant) ScSubstitutor.empty else ScalaPsiUtil.inferMethodTypesArgs(method, scalaLookupItem.substitutor)
              checkType(method.getReturnType.toScType(), infer, second)
            case typed: ScTypedDefinition =>
              if (!PsiTreeUtil.isContextAncestor(typed.nameContext, place, false) &&
                (originalPlace == null || !PsiTreeUtil.isContextAncestor(typed.nameContext, originalPlace, false)))
                for (tt <- typed.`type`()) checkType(tt, ScSubstitutor.empty, checkForSecondCompletion)
            case f: PsiField =>
              checkType(f.getType.toScType(), ScSubstitutor.empty, checkForSecondCompletion)
            case _ =>
          }
        }
      }

      variant match {
        case el: ScalaLookupItem => handleVariant(el)
        case ch: ScalaChainLookupElement => handleVariant(ch.element, chainVariant = true)
        case _ =>
      }
    }

    def createLookup(namedElement: PsiNamedElement,
                     owner: PsiClass): Unit = {
      val lookup = new ScalaResolveResult(namedElement)
        .getLookupElement(isClassName = true, shouldImport = true)
        .head
      lookup.addLookupStrings(s"${owner.name}.${namedElement.name}")
      applyVariant(lookup)
    }


    place.getContext match {
      case ref: ScReferenceExpression if ref.smartQualifier.isEmpty =>
        //enum and factory methods
        val iterator = typez.iterator
        while (iterator.hasNext) {
          val tp = iterator.next()

          def checkObject(o: ScObject): Unit = if (ResolveUtils.isAccessible(o, place, forCompletion = true) && ScalaPsiUtil.hasStablePath(o)) {
            o.members.flatMap {
              case function: ScFunction => Seq(function)
              case v: ScValueOrVariable => v.declaredElements
              case obj: ScObject => Seq(obj)
              case _ => Seq.empty
            }.foreach {
              createLookup(_, o)
            }
          }

          def checkTypeProjection(tp: ScType) {
            tp match {
              case ScProjectionType(proj, _: ScTypeAlias | _: ScClass | _: ScTrait) =>
                proj.extractClass match {
                  case Some(o: ScObject) => checkObject(o)
                  case _ =>
                }
              case _ =>
            }
          }
          @tailrec
          def checkType(tp: ScType) {
            def isValid(member: PsiMember) =
              member.hasModifierProperty("static") && ResolveUtils.isAccessible(member, place, forCompletion = true)

            tp.extractClass match {
              case Some(c: ScClass) if c.qualifiedName == "scala.Option" || c.qualifiedName == "scala.Some" =>
                tp match {
                  case ParameterizedType(_, Seq(scType)) => checkType(scType)
                  case _ =>
                }
              case Some(_: ScObject) => //do nothing
              case Some(clazz: ScTypeDefinition) =>
                checkTypeProjection(tp)
                ScalaPsiUtil.getCompanionModule(clazz) match {
                  case Some(o: ScObject) => checkObject(o)
                  case _ => //do nothing
                }
              case Some(p: PsiClass) if ResolveUtils.isAccessible(p, place, forCompletion = true) =>
                (p.getAllMethods ++ p.getFields).filter(isValid)
                  .foreach(createLookup(_, p))
              case _ => checkTypeProjection(tp)
            }
          }
          checkType(tp)
        }
        variants.foreach(applyVariant(_, checkForSecondCompletion = true))
        if (typez.exists(_.equiv(Boolean))) {
          for (keyword <- Set("false", "true")) {
            result.addElement(ScalaKeywordLookupItem.getLookupElement(keyword)(place.getProject))
          }
        }
        if (completeThis) {
          var parent = place
          var foundClazz = false
          while (parent != null) {
            parent match {
              case _: ScNewTemplateDefinition if foundClazz => //do nothing, impossible to invoke
              case t: ScTemplateDefinition =>
                t.getTypeWithProjections(thisProjections = true) match {
                  case Right(scType) =>
                    val lookupString = (if (foundClazz) t.name + "." else "") + "this"
                    val el = new ScalaLookupItem(t, lookupString)
                    if (!scType.equiv(Nothing) && typez.exists(scType conforms _)) {
                      if (!foundClazz) el.bold = true
                      result.addElement(el)
                    } else {
                      var elementAdded = false
                      typez.foreach {
                        case ParameterizedType(tp, Seq(arg)) if !elementAdded =>
                          tp.extractClass match {
                            case Some(clazz) if clazz.qualifiedName == "scala.Option" || clazz.qualifiedName == "scala.Some" =>
                              if (!scType.equiv(Nothing) && scType.conforms(arg)) {
                                el.someSmartCompletion = true
                                result.addElement(el)
                                elementAdded = true
                              }
                            case _ =>
                          }
                        case _ =>
                      }
                    }
                  case _ =>
                }
                foundClazz = true
              case _ =>
            }
            parent = parent.getContext
          }
        }
      case _ => variants.foreach(applyVariant(_, checkForSecondCompletion = true))
    }
  }


  /*
    ref = expr
    expr = ref
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScAssignStmt]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      val element = positionFromParameters(parameters)

      extractReference[ScAssignStmt](element).foreach { case ReferenceWithElement(ref, assign) =>
        if (assign.getRExpression.contains(ref)) {
          assign.getLExpression match {
            case _: ScMethodCall => //todo: it's update method
            case _: ScExpression =>
              //we can expect that the type is same for left and right parts.
              acceptTypes(ref.expectedTypes(), ref.getVariants, result,
                ref.resolveScope, parameters.getInvocationCount > 1, ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
          }
        } else {
          //so it's left expression
          //todo: if right expression exists?
        }
      }
    }
  })

  /*
    val x: Type = ref
    var y: Type = ref
   */
  extend(CompletionType.SMART, StandardPatterns.or[PsiElement](superParentPattern(classOf[ScPatternDefinition]),
    superParentPattern(classOf[ScVariableDefinition])), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      val element = positionFromParameters(parameters)
      extractReference[PsiElement](element).foreach { case ReferenceWithElement(ref, _) =>
        acceptTypes(ref.expectedType().toList, ref.getVariants, result, ref.resolveScope,
          parameters.getInvocationCount > 1, ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
      }
    }
  })

  /*
    return ref
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScReturnStmt]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val element = positionFromParameters(parameters)
      extractReference[ScReturnStmt](element).foreach { case ReferenceWithElement(ref, _) =>
         val fun: ScFunction = PsiTreeUtil.getContextOfType(ref, classOf[ScFunction])
          if (fun == null) return
          acceptTypes(Seq[ScType](fun.returnType.getOrAny), ref.getVariants, result,
            ref.resolveScope, parameters.getInvocationCount > 1, ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
      }
    }
  })

  private def argumentsForFunction(args: ScArgumentExprList, referenceExpression: ScReferenceExpression,
                                   result: CompletionResultSet) {
    val braceArgs = args.isBraceArgs
    val expects = referenceExpression.expectedTypes()
    for (expected <- expects) {
      def params(tp: ScType): Seq[ScType] = tp match {
        case FunctionType(_, params) => params
        case _ => null
      }
      val actualParams = params(expected)
      if (actualParams != null) {
        val params = actualParams match {
          case Seq(TupleType(types)) if braceArgs => types
          case _ => actualParams
        }
        val presentableParams = params.map(_.removeAbstracts)
        val anonFunRenderer = new LookupElementRenderer[LookupElement] {
          def renderElement(element: LookupElement, presentation: LookupElementPresentation) {
            val arrowText = ScalaPsiUtil.functionArrow(referenceExpression.getProject)
            val text = ScalaCompletionUtil.generateAnonymousFunctionText(braceArgs, presentableParams, canonical = false,
              arrowText = arrowText)
            presentation match {
              case realPresentation: RealLookupElementPresentation =>
                if (!realPresentation.hasEnoughSpaceFor(text, false)) {
                  var prefixIndex = presentableParams.length - 1
                  val suffix = s", ... $arrowText"
                  var end = false
                  while (prefixIndex > 0 && !end) {
                    val prefix = ScalaCompletionUtil.generateAnonymousFunctionText(braceArgs,
                      presentableParams.slice(0, prefixIndex), canonical = false, withoutEnd = true,
                      arrowText = arrowText)
                    if (realPresentation.hasEnoughSpaceFor(prefix + suffix, false)) {
                      presentation.setItemText(prefix + suffix)
                      end = true
                    } else prefixIndex -= 1
                  }
                  if (!end) {
                    presentation.setItemText(s"... $arrowText ")
                  }
                } else presentation.setItemText(text)
                presentation.setIcon(Icons.LAMBDA)
              case _ =>
                presentation.setItemText(text)
            }
          }
        }
        val builder = LookupElementBuilder.create("")
                .withRenderer(anonFunRenderer)
                .withInsertHandler(new ScalaGenerateAnonymousFunctionInsertHandler(params, braceArgs))
        val lookupElement =
          if (ApplicationManager.getApplication.isUnitTestMode)
            builder.withAutoCompletionPolicy(AutoCompletionPolicy.ALWAYS_AUTOCOMPLETE)
          else builder.withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE)
        result.addElement(lookupElement)
      }
    }
  }

  /*
    call(exprs, ref, exprs)
    if expected type is function, so we can suggest anonymous function creation
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScArgumentExprList]),
    new CompletionProvider[CompletionParameters] {
      def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                         result: CompletionResultSet) {
        val element = positionFromParameters(parameters)
        extractReference[ScArgumentExprList](element).foreach { case ReferenceWithElement(referenceExpression, args) =>
          argumentsForFunction(args, referenceExpression, result)
        }
      }
  })

  /*
    call {ref}
    if expected type is function, so we can suggest anonymous function creation
   */
  extend(CompletionType.SMART, bracesCallPattern,
    new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      val element = positionFromParameters(parameters)
      val referenceExpression = element.getContext.asInstanceOf[ScReferenceExpression]
      val block = referenceExpression.getContext.asInstanceOf[ScBlockExpr]
      val args = block.getContext.asInstanceOf[ScArgumentExprList]
      argumentsForFunction(args, referenceExpression, result)
    }
  })

  /*
    call(exprs, ref, exprs)
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScArgumentExprList]),
    new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      val element = positionFromParameters(parameters)
      extractReference[ScArgumentExprList](element).foreach { case ReferenceWithElement(referenceExpression, _) =>
        acceptTypes(referenceExpression.expectedTypes(), referenceExpression.getVariants, result,
          referenceExpression.resolveScope, parameters.getInvocationCount > 1, ScalaCompletionUtil.completeThis(referenceExpression),
          element, parameters.getOriginalPosition)
      }
    }
  })

  /*
    if (ref) expr
    if (expr) ref
    if (expr) expr else ref
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScIfStmt]),
    new CompletionProvider[CompletionParameters] {
      def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                         result: CompletionResultSet) {
        val element = positionFromParameters(parameters)
        extractReference[ScIfStmt](element).foreach { case ReferenceWithElement(ref, ifStmt) =>
          if (ifStmt.condition.getOrElse(null: ScExpression) == ref)
            acceptTypes(ref.expectedTypes(), ref.getVariants, result,
            ref.resolveScope, parameters.getInvocationCount > 1, ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
          else acceptTypes(ifStmt.expectedTypes(), ref.getVariants, result,
            ref.resolveScope, parameters.getInvocationCount > 1, ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
        }
      }
    })

  /*
    while (ref) expr
    while (expr) ref
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScWhileStmt]),
    new CompletionProvider[CompletionParameters] {
      def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                         result: CompletionResultSet) {
        val element = positionFromParameters(parameters)
        extractReference[ScWhileStmt](element).foreach { case ReferenceWithElement(ref, whileStmt) =>
          if (whileStmt.condition.getOrElse(null: ScExpression) == ref)
            acceptTypes(ref.expectedTypes(), ref.getVariants, result,
              ref.resolveScope, parameters.getInvocationCount > 1, ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
          else acceptTypes(ref.expectedTypes(), ref.getVariants, result,
            ref.resolveScope, parameters.getInvocationCount > 1, ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
        }
      }
    })

  /*
   do expr while (ref)
   do ref while (expr)
  */
  extend(CompletionType.SMART, superParentPattern(classOf[ScDoStmt]),
    new CompletionProvider[CompletionParameters] {
      def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                         result: CompletionResultSet) {
        val element = positionFromParameters(parameters)
        extractReference[ScDoStmt](element).foreach { case ReferenceWithElement(ref, doStmt) =>
          if (doStmt.condition.getOrElse(null: ScExpression) == ref)
            acceptTypes(ref.expectedTypes(), ref.getVariants, result,
              ref.resolveScope, parameters.getInvocationCount > 1, ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
          else acceptTypes(ref.expectedTypes(), ref.getVariants, result,
            ref.resolveScope, parameters.getInvocationCount > 1, ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
        }
      }
    })

  /*
    expr op ref
    expr ref name
    ref op expr
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScInfixExpr]),
    new CompletionProvider[CompletionParameters] {
      def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val element = positionFromParameters(parameters)
        extractReference[ScInfixExpr](element).foreach { case ReferenceWithElement(ref, infix) =>
          val typez: ArrayBuffer[ScType] = new ArrayBuffer[ScType]
          if (infix.left == ref) {
            val op: String = infix.operation.getText
            if (op.endsWith(":")) {
              typez ++= ref.expectedTypes()
            }
          } else if (infix.right == ref) {
            val op: String = infix.operation.getText
            if (!op.endsWith(":")) {
              typez ++= ref.expectedTypes()
            }
          }
          acceptTypes(typez, ref.getVariants, result, ref.resolveScope, parameters.getInvocationCount > 1,
            ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
        }
      }
    })

  /*
    inside try block according to expected type
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScTryBlock]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      val element = positionFromParameters(parameters)
      extractReference[ScTryBlock](element).foreach { case ReferenceWithElement(ref, _) =>
        acceptTypes(ref.expectedTypes(), ref.getVariants, result, ref.resolveScope, parameters.getInvocationCount > 1,
          ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
      }
    }
  })

  /*
   inside block expression according to expected type
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScBlockExpr]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      val element = positionFromParameters(parameters)
      extractReference[ScBlockExpr](element).foreach { case ReferenceWithElement(ref, _) =>
        acceptTypes(ref.expectedTypes(), ref.getVariants, result, ref.resolveScope, parameters.getInvocationCount > 1,
          ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
      }
    }
  })

  /*
   inside finally block
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScFinallyBlock]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      val element = positionFromParameters(parameters)
      extractReference[ScFinallyBlock](element).foreach { case ReferenceWithElement(ref, _) =>
        acceptTypes(ref.expectedTypes(), ref.getVariants, result, ref.resolveScope, parameters.getInvocationCount > 1,
          ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
      }
    }
  })

  /*
   inside anonymous function
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScFunctionExpr]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val element = positionFromParameters(parameters)
      extractReference[ScFunctionExpr](element).foreach { case ReferenceWithElement(ref, _) =>
        acceptTypes(ref.expectedType().toList, ref.getVariants, result, ref.resolveScope, parameters.getInvocationCount > 1,
          ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
      }
    }
  })

  /*
   for function definitions
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScFunctionDefinition]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val element = positionFromParameters(parameters)
      extractReference[ScFunctionDefinition](element).foreach { case ReferenceWithElement(ref, _) =>
        acceptTypes(ref.expectedTypes(), ref.getVariants, result, ref.resolveScope, parameters.getInvocationCount > 1,
          ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
      }
    }
  })

  /*
   for default parameters
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScParameter]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val element = positionFromParameters(parameters)
      extractReference[ScParameter](element).foreach { case ReferenceWithElement(ref, _) =>
        acceptTypes(ref.expectedTypes(), ref.getVariants, result, ref.resolveScope, parameters.getInvocationCount > 1,
          ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
      }
    }
  })

  extend(
    CompletionType.SMART,
    ScalaAfterNewCompletionUtil.afterNewPattern,
    new CompletionProvider[CompletionParameters] {

      def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {
        val element = positionFromParameters(parameters)
        val items = ScalaAfterNewCompletionUtil.lookupsAfterNew(element)
        result.addAllElements(items)
      }
    }
  )
}

object ScalaSmartCompletionContributor {
  case class ReferenceWithElement[T <: PsiElement](reference: ScReferenceExpression, element: T)

  def isAccessible(el: ScalaLookupItem, place: PsiElement): Boolean = {
    ScalaPsiUtil.nameContext(el.element) match {
      case memb: ScMember => ResolveUtils.isAccessible(memb, place, forCompletion = true)
      case _ => true
    }
  }

  def extractReference[T <: PsiElement](element: PsiElement): Option[ReferenceWithElement[T]] = {
    element.getContext.asOptionOf[ScReferenceExpression].flatMap { reference =>
      reference.getContext match {
        case refExpr: ScReferenceExpression =>
          Option(ReferenceWithElement(refExpr, reference.getContext.getContext.asInstanceOf[T]))
        case _ =>
          Option(ReferenceWithElement(reference, reference.getContext.asInstanceOf[T]))
      }
    }
  }

  def superParentPattern(clazz: java.lang.Class[_ <: PsiElement]): ElementPattern[PsiElement] = {
    StandardPatterns.or(PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER).withParent(classOf[ScReferenceExpression]).
      withSuperParent(2, clazz),
      PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER).withParent(classOf[ScReferenceExpression]).
        withSuperParent(2, classOf[ScReferenceExpression]).withSuperParent(3, clazz))
  }

  def superParentsPattern(classes: Class[_ <: PsiElement]*): ElementPattern[PsiElement] = {
    var pattern: Capture[PsiElement] =
      PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER).withParent(classes(0))
    for (i <- 1 until classes.length) {
      pattern = pattern.withSuperParent(i + 1, classes(i))
    }
    pattern
  }

  val bracesCallPattern: ElementPattern[PsiElement] = superParentsPattern(classOf[ScReferenceExpression], classOf[ScBlockExpr],
    classOf[ScArgumentExprList], classOf[ScMethodCall])
}
