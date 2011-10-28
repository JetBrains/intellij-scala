package org.jetbrains.plugins.scala.lang.completion

import handlers.{ScalaGenerateAnonymousFunctionInsertHandler, ScalaConstuctorInsertHandler}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.codeInsight.completion._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi._
import api.base.types.ScSimpleTypeElement
import api.base.{ScConstructor, ScStableCodeReferenceElement}
import api.statements._
import api.toplevel.templates.{ScExtendsBlock, ScClassParents}
import api.toplevel.typedef.{ScTrait, ScObject}
import api.toplevel.ScTypedDefinition
import params.ScParameter
import types._
import result.TypingContext
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.patterns.{ElementPattern, StandardPatterns, PlatformPatterns}
import com.intellij.patterns.PsiElementPattern.Capture
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils
import com.intellij.psi._
import com.intellij.openapi.application.ApplicationManager
import search.searches.ClassInheritorsSearch
import com.intellij.util.{Processor, ProcessingContext}
import com.intellij.codeInsight.lookup._
import org.jetbrains.plugins.scala.icons.Icons
import collection.mutable.{HashSet, ArrayBuffer}

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.09.2009
 */

class ScalaSmartCompletionContributor extends CompletionContributor {
  import ScalaSmartCompletionContributor._
  override def beforeCompletion(context: CompletionInitializationContext) {

  }

  private def acceptTypes(typez: Seq[ScType], variants: Array[Object], result: CompletionResultSet,
                          scope: GlobalSearchScope, completeSome: Boolean) {
    if (typez.length == 0 || typez.forall(_ == types.Nothing)) return
    for (variant <- variants) {
      variant match {
        case (el: LookupElement, elem: PsiElement, subst: ScSubstitutor) => {
          def checkType(tp: ScType) {
            val scType = subst.subst(tp)
            import types.Nothing
            if (!scType.equiv(Nothing) && typez.find(scType conforms _) != None) {
              result.addElement(el)
            } else if (completeSome) {
              var elementAdded = false
              typez.foreach {
                case ScParameterizedType(tp, Seq(arg)) if !elementAdded =>
                  ScType.extractClass(tp, Some(elem.getProject)) match {
                    case Some(clazz) if clazz.getQualifiedName == "scala.Option" =>
                      if (!scType.equiv(Nothing) && scType.conforms(arg)) {
                        el.putUserData(ResolveUtils.someSmartCompletionKey, java.lang.Boolean.TRUE)

                        result.addElement(el)
                        elementAdded = true
                      }
                    case None =>
                  }
                case _ =>
              }
            }
          }
          val userData = el.getUserData(ResolveUtils.isNamedParameterOrAssignment)
          if (userData == null || !userData.booleanValue())
            elem match {
              case fun: ScSyntheticFunction => checkType(fun.retType)
              case fun: ScFunction => checkType(fun.returnType.getOrAny)
              case meth: PsiMethod => checkType(ScType.create(meth.getReturnType, meth.getProject, scope))
              case typed: ScTypedDefinition => for (tt <- typed.getType(TypingContext.empty)) checkType(tt)
              case _ =>
            }
        }
        case _ =>
      }
    }
  }



  /*
    ref = expr
    expr = ref
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScAssignStmt]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      val assign = ref.getParent.asInstanceOf[ScAssignStmt]
      if (assign.getRExpression == Some(ref)) {
        assign.getLExpression match {
          case call: ScMethodCall => //todo: it's update method
          case leftExpression: ScExpression => {
            //we can expect that the type is same for left and right parts.
            acceptTypes(ref.expectedType().toList.toSeq, ref.getVariants, result,
              ref.getResolveScope, parameters.getInvocationCount > 1)
          }
        }
      } else { //so it's left expression
        //todo: if right expression exists?
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
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedType().toList.toSeq, ref.getVariants, result, ref.getResolveScope,
        parameters.getInvocationCount > 1)
    }
  })

  /*
    return ref
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScReturnStmt]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      val fun: ScFunction = PsiTreeUtil.getParentOfType(ref, classOf[ScFunction])
      if (fun == null) return
      acceptTypes(Seq[ScType](fun.returnType.getOrAny), ref.getVariants, result,
        ref.getResolveScope, parameters.getInvocationCount > 1)
    }
  })

  private def argumentsForFunction(args: ScArgumentExprList, referenceExpression: ScReferenceExpression,
                           result: CompletionResultSet) {
    val braceArgs = args.isBraceArgs
    val expects = referenceExpression.expectedTypes()
    for (expected <- expects) {
      val actualParams: Seq[ScType] = expected match {
        case ScFunctionType(_, params) => params
        case p: ScParameterizedType if p.getFunctionType != None =>
          p.getFunctionType match {
            case Some(ScFunctionType(_, params)) => params
            case _ => null
          }
        case _ => null
      }
      if (actualParams != null) {
        val params = actualParams match {
          case Seq(ScTupleType(params)) if braceArgs => params
          case Seq(p: ScParameterizedType) if p.getTupleType != None => p.getTupleType match {
            case Some(ScTupleType(params)) if braceArgs => params
            case _ => actualParams
          }
          case _ => actualParams
        }
        val presentableParams = params.map(_.removeAbstracts)
        var builder = LookupElementBuilder.create("")
        builder = builder.setRenderer(new LookupElementRenderer[LookupElement] {
          def renderElement(element: LookupElement, presentation: LookupElementPresentation) {
            val text = ScalaCompletionUtil.generateAnonymousFunctionText(braceArgs, presentableParams, false)
            presentation match {
              case realPresentation: RealLookupElementPresentation =>
                if (!realPresentation.hasEnoughSpaceFor(text, false)) {
                  var prefixIndex = presentableParams.length - 1
                  val suffix = ", ... =>"
                  var end = false
                  while (prefixIndex > 0 && !end) {
                    val prefix = ScalaCompletionUtil.generateAnonymousFunctionText(braceArgs,
                      presentableParams.slice(0, prefixIndex), false, true)
                    if (realPresentation.hasEnoughSpaceFor(prefix + suffix, false)) {
                      presentation.setItemText(prefix + suffix)
                      end = true
                    } else prefixIndex -= 1
                  }
                  if (!end) {
                    presentation.setItemText("... => ")
                  }
                } else presentation.setItemText(text)
                presentation.setIcon(Icons.LAMBDA)
              case _ =>
                presentation.setItemText(text)
            }
          }
        })
        builder = builder.setInsertHandler(new ScalaGenerateAnonymousFunctionInsertHandler(params, braceArgs))
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
      val element = parameters.getPosition
      val referenceExpression = element.getParent.asInstanceOf[ScReferenceExpression]
      val args = referenceExpression.getParent.asInstanceOf[ScArgumentExprList]
      argumentsForFunction(args, referenceExpression, result)
    }
  })

  extend(CompletionType.SMART, PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER),
    new CompletionProvider[CompletionParameters]() {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      "for test"
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
      val element = parameters.getPosition
      val referenceExpression = element.getParent.asInstanceOf[ScReferenceExpression]
      val block = referenceExpression.getParent.asInstanceOf[ScBlockExpr]
      val args = block.getParent.asInstanceOf[ScArgumentExprList]
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
      val element = parameters.getPosition
      val referenceExpression = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(referenceExpression.expectedTypes(), referenceExpression.getVariants, result,
        referenceExpression.getResolveScope, parameters.getInvocationCount > 1)
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
        val element = parameters.getPosition
        val ref = element.getParent.asInstanceOf[ScReferenceExpression]
        val ifStmt = ref.getParent.asInstanceOf[ScIfStmt]
        if (ifStmt.condition.getOrElse(null: ScExpression) == ref)
          acceptTypes(ref.expectedTypes(), ref.getVariants, result,
            ref.getResolveScope, parameters.getInvocationCount > 1)
        else acceptTypes(ifStmt.expectedTypes(), ref.getVariants, result,
          ref.getResolveScope, parameters.getInvocationCount > 1)
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
        val element = parameters.getPosition
        val ref = element.getParent.asInstanceOf[ScReferenceExpression]
        val infix = ref.getParent.asInstanceOf[ScInfixExpr]
        val typez: ArrayBuffer[ScType] = new ArrayBuffer[ScType]
        if (infix.lOp == ref) {
          val op: String = infix.operation.getText
          if (op.endsWith(":")) {
            typez ++= ref.expectedTypes()
          }
        } else if (infix.rOp == ref) {
          val op: String = infix.operation.getText
          if (!op.endsWith(":")) {
            typez ++= ref.expectedTypes()
          }
        }
        acceptTypes(typez, ref.getVariants, result, ref.getResolveScope, parameters.getInvocationCount > 1)
      }
    })

  /*
    inside try block according to expected type
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScTryBlock]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedTypes(), ref.getVariants, result, ref.getResolveScope, parameters.getInvocationCount > 1)
    }
  })

  /*
   inside block expression according to expected type
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScBlockExpr]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedTypes(), ref.getVariants, result, ref.getResolveScope, parameters.getInvocationCount > 1)
    }
  })

  /*
   inside finally block
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScFinallyBlock]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedTypes(), ref.getVariants, result, ref.getResolveScope, parameters.getInvocationCount > 1)
    }
  })

  /*
   inside anonymous function
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScFunctionExpr]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedType().toList.toSeq, ref.getVariants, result, ref.getResolveScope, parameters.getInvocationCount > 1)
    }
  })

  /*
   for function definitions
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScFunctionDefinition]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedTypes(), ref.getVariants, result, ref.getResolveScope, parameters.getInvocationCount > 1)
    }
  })

  /*
   for default parameters
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScParameter]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedTypes(), ref.getVariants, result, ref.getResolveScope, parameters.getInvocationCount > 1)
    }
  })

  extend(CompletionType.SMART, afterNewPattern, new CompletionProvider[CompletionParameters] {
      def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val element = parameters.getPosition
        val addedClasses = new HashSet[String]
        val newExpr = PsiTreeUtil.getParentOfType(element, classOf[ScNewTemplateDefinition])
        val types: Array[ScType] = newExpr.expectedTypes().map(tp => tp match {
          case ScAbstractType(_, lower, upper) => upper
          case _ => tp
        })
        for (typez <- types) {
          val element: LookupElement = convertTypeToLookupElement(typez, newExpr, addedClasses,
            new AfterNewLookupElementRenderer(_, _, _), new ScalaConstuctorInsertHandler)
          if (element != null) {
            result.addElement(element)
          }
        }

        for (typez <- types) {
          collectInheritorsForType(typez, newExpr, addedClasses, result, new AfterNewLookupElementRenderer(_, _, _),
            new ScalaConstuctorInsertHandler)
        }
      }
    })
}

private[completion] object ScalaSmartCompletionContributor {
  def superParentPattern(clazz: java.lang.Class[_ <: PsiElement]): ElementPattern[PsiElement] = {
    PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER).withParent(classOf[ScReferenceExpression]).
          withSuperParent(2, clazz)
  }

  def superParentsPattern(classes: Class[_ <: PsiElement]*): ElementPattern[PsiElement] = {
    var pattern: Capture[PsiElement] =
      PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER).withParent(classes(0))
    for (i <- 1 until classes.length) {
      pattern = pattern.withSuperParent(i + 1, classes(i))
    }
    pattern
  }
  val afterNewPattern = superParentsPattern(classOf[ScStableCodeReferenceElement], classOf[ScSimpleTypeElement],
    classOf[ScConstructor], classOf[ScClassParents], classOf[ScExtendsBlock], classOf[ScNewTemplateDefinition])

  val bracesCallPattern = superParentsPattern(classOf[ScReferenceExpression], classOf[ScBlockExpr],
    classOf[ScArgumentExprList], classOf[ScMethodCall])

  def getLookupElementFromClass(expectedTypes: Array[ScType], clazz: PsiClass): LookupElement = {
    val undefines: Seq[ScUndefinedType] = clazz.getTypeParameters.map(ptp =>
      new ScUndefinedType(new ScTypeParameterType(ptp, ScSubstitutor.empty))
    )
    val predefinedType =
      if (clazz.getTypeParameters.length == 1) {
        ScParameterizedType(ScDesignatorType(clazz), undefines)
      }
      else
        ScDesignatorType(clazz)
    val noUndefType =
      if (clazz.getTypeParameters.length == 1) {
        ScParameterizedType(ScDesignatorType(clazz), clazz.getTypeParameters.map(ptp =>
          new ScTypeParameterType(ptp, ScSubstitutor.empty)
        ))
      }
      else
        ScDesignatorType(clazz)

    val iterator = expectedTypes.iterator
    while (iterator.hasNext) {
      val typez = iterator.next()
      if (predefinedType.conforms(typez)) {
        val undef = Conformance.undefinedSubst(typez, predefinedType)
        undef.getSubstitutor match {
          case Some(subst) =>
            val lookupElement = getLookupElementFromTypeAndClass(subst.subst(noUndefType), clazz,
              ScSubstitutor.empty, new AfterNewLookupElementRenderer(_, _, _), new ScalaConstuctorInsertHandler)
            for (undefine <- undefines) {
              subst.subst(undefine) match {
                case ScUndefinedType(_) =>
                  lookupElement.putUserData(ResolveUtils.typeParametersProblemKey,
                    new java.lang.Boolean(true))
                case _ =>
              }
            }
            return lookupElement
          case _ =>
        }
      }
    }
    val lookupElement = getLookupElementFromTypeAndClass(noUndefType, clazz, ScSubstitutor.empty,
      new AfterNewLookupElementRenderer(_, _, _), new ScalaConstuctorInsertHandler)
    if (undefines.length > 0) {
      lookupElement.putUserData(ResolveUtils.typeParametersProblemKey,
        new java.lang.Boolean(true))
    }
    lookupElement
  }

  class AfterNewLookupElementRenderer(tp: ScType, psiClass: PsiClass,
                                      subst: ScSubstitutor) extends LookupElementRenderer[LookupElement] {
    def renderElement(ignore: LookupElement, presentation: LookupElementPresentation) {
      var isDeprecated = false
      psiClass match {
        case doc: PsiDocCommentOwner if doc.isDeprecated => isDeprecated = true
        case _ =>
      }
      var tailText: String = ""
      val itemText: String = psiClass.getName + (tp match {
        case ScParameterizedType(_, tps) =>
          tps.map(tp => ScType.presentableText(subst.subst(tp))).mkString("[", ", ", "]")
        case _ => ""
      })
      psiClass match {
        case clazz: PsiClass => {
          if (psiClass.isInterface || psiClass.isInstanceOf[ScTrait] ||
            psiClass.hasModifierProperty("abstract")) {
            tailText += " {...}"
          }
          val location: String = clazz.getPresentation.getLocationString
          presentation.setTailText(tailText + " " + location, true)
        }
        case _ =>
      }
      presentation.setIcon(psiClass.getIcon(0))
      presentation.setStrikeout(isDeprecated)
      presentation.setItemText(itemText)
    }
  }


  def getLookupElementFromTypeAndClass(tp: ScType, psiClass: PsiClass, subst: ScSubstitutor,
                                renderer: (ScType, PsiClass, ScSubstitutor) => LookupElementRenderer[LookupElement],
                                insertHandler: InsertHandler[LookupElement]): LookupElement = {
    val name: String = psiClass.getName
    var lookupBuilder: LookupElementBuilder = LookupElementBuilder.create(psiClass, name)
    lookupBuilder = lookupBuilder.setRenderer(renderer(tp, psiClass, subst))
    var lookupElement: LookupElement = lookupBuilder
    if (ApplicationManager.getApplication.isUnitTestMode || psiClass.isInterface ||
      psiClass.isInstanceOf[ScTrait] || psiClass.hasModifierProperty("abstract"))
      lookupElement =
        (if (ApplicationManager.getApplication.isUnitTestMode) AutoCompletionPolicy.ALWAYS_AUTOCOMPLETE
        else AutoCompletionPolicy.NEVER_AUTOCOMPLETE).applyPolicy(lookupBuilder)
    lookupElement = LookupElementDecorator.withInsertHandler(lookupElement, new ScalaConstuctorInsertHandler)
    tp match {
      case ScParameterizedType(_, tps) =>
        lookupElement.putUserData(ResolveUtils.typeParametersKey, tps)
      case _ =>
    }
    lookupElement
  }

  def convertTypeToLookupElement(tp: ScType, place: PsiElement, addedClasses: HashSet[String],
                                 renderer: (ScType, PsiClass, ScSubstitutor) => LookupElementRenderer[LookupElement],
                                 insertHandler: InsertHandler[LookupElement]): LookupElement = {
    ScType.extractClassType(tp, Some(place.getProject)) match {
      case Some((clazz: PsiClass, subst: ScSubstitutor)) =>
        //filter base types (it's important for scala 2.9)
        clazz.getQualifiedName match {
          case "scala.Boolean" | "scala.Int" | "scala.Long" | "scala.Byte" | "scala.Short" | "scala.AnyVal" |
               "scala.Char" | "scala.Unit" | "scala.Float" | "scala.Double" | "scala.Any" => return null
          case _ =>
        }
        //todo: filter inner classes smarter (how? don't forget deep inner classes)
        if (clazz.getContainingClass != null && (!clazz.getContainingClass.isInstanceOf[ScObject] ||
          clazz.hasModifierProperty("static"))) return null
        if (!ResolveUtils.isAccessible(clazz, place)) return null
        if (addedClasses.contains(clazz.getQualifiedName)) return null
        addedClasses += clazz.getQualifiedName
        getLookupElementFromTypeAndClass(tp, clazz, subst, renderer, insertHandler)
      case _ => null
    }
  }

  def collectInheritorsForType(typez: ScType, place: PsiElement, addedClasses: HashSet[String],
                               result: CompletionResultSet,
                               renderer: (ScType, PsiClass, ScSubstitutor) => LookupElementRenderer[LookupElement],
                               insertHandler: InsertHandler[LookupElement]) {
    ScType.extractClassType(typez, Some(place.getProject)) match {
      case Some((clazz, subst)) =>
        ClassInheritorsSearch.search(clazz, true).forEach(new Processor[PsiClass] {
          def process(clazz: PsiClass): Boolean = {
            if (clazz.getName == null || clazz.getName == "") return true
            val undefines: Seq[ScUndefinedType] = clazz.getTypeParameters.map(ptp =>
              new ScUndefinedType(new ScTypeParameterType(ptp, ScSubstitutor.empty))
            )
            val predefinedType =
              if (clazz.getTypeParameters.length == 1) {
                ScParameterizedType(ScDesignatorType(clazz), undefines)
              }
              else
                ScDesignatorType(clazz)
            val noUndefType =
              if (clazz.getTypeParameters.length == 1) {
                ScParameterizedType(ScDesignatorType(clazz), clazz.getTypeParameters.map(ptp =>
                  new ScTypeParameterType(ptp, ScSubstitutor.empty)
                ))
              }
              else
                ScDesignatorType(clazz)

            if (!predefinedType.conforms(typez)) return true
            val undef = Conformance.undefinedSubst(typez, predefinedType)
            undef.getSubstitutor match {
              case Some(undefSubst) =>
                val lookupElement = convertTypeToLookupElement(undefSubst.subst(noUndefType), place, addedClasses,
                  renderer, insertHandler)
                if (lookupElement != null) {
                  for (undefine <- undefines) {
                    undefSubst.subst(undefine) match {
                      case ScUndefinedType(_) =>
                        lookupElement.putUserData(ResolveUtils.typeParametersProblemKey, new java.lang.Boolean(true))
                      case _ =>
                    }
                  }
                  result.addElement(lookupElement)
                }
              case _ =>
            }
            true
          }
        })
      case _ =>
    }
  }
}