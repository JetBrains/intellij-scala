package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup._
import com.intellij.codeInsight.template._
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.patterns.ElementPattern
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.completion.lookups.{ScalaChainLookupElement, ScalaKeywordLookupItem, ScalaLookupItem}
import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScTypedPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaPsiElement, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult, StdKinds}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._

final class ScalaSmartCompletionContributor extends ScalaCompletionContributor {

  import ScalaSmartCompletionContributor._

  /*
    ref = expr
    expr = ref
   */
  extend(
    classOf[ScAssignment],
    new CompletionProvider[CompletionParameters] {
      override def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                                  result: CompletionResultSet): Unit = {
        val element = positionFromParameters(parameters)

        extractReference[ScAssignment](element).foreach { case (ref, assign) =>
          if (assign.rightExpression.contains(ref)) {
            assign.leftExpression match {
              case _: ScMethodCall => //todo: it's update method
              case _: ScExpression =>
                //we can expect that the type is same for left and right parts.
                acceptTypes(ref.expectedTypes(), ref.getVariants, result, parameters.getInvocationCount > 1, ScalaCompletionUtil.hasNoQualifier(ref), parameters.getOriginalPosition)(element)
            }
          } else {
            //so it's left expression
            //todo: if right expression exists?
          }
        }
      }
    }
  )

  /*
    val x: Type = ref
    var y: Type = ref
   */
  extend(
    superParentPattern(classOf[ScPatternDefinition]) || superParentPattern(classOf[ScVariableDefinition]),
    new CompletionProvider[CompletionParameters] {
      override def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                                  result: CompletionResultSet): Unit = {
        val element = positionFromParameters(parameters)
        extractReference[PsiElement](element).foreach { case (ref, _) =>
          acceptTypes(ref.expectedType().toList, ref.getVariants, result,
            parameters.getInvocationCount > 1, ScalaCompletionUtil.hasNoQualifier(ref), parameters.getOriginalPosition)(element)
        }
      }
    })

  /*
    return ref
   */
  extend(
    classOf[ScReturn],
    new CompletionProvider[CompletionParameters] {
      override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {
        val element = positionFromParameters(parameters)
        extractReference[ScReturn](element).foreach { case (ref, _) =>
          val fun: ScFunction = PsiTreeUtil.getContextOfType(ref, classOf[ScFunction])
          if (fun == null) return
          acceptTypes(Seq[ScType](fun.returnType.getOrAny), ref.getVariants, result, parameters.getInvocationCount > 1, ScalaCompletionUtil.hasNoQualifier(ref), parameters.getOriginalPosition)(element)
        }
      }
    }
  )

  /*
    call(exprs, ref, exprs)
    if expected type is function, so we can suggest anonymous function creation
   */
  extend(
    classOf[ScArgumentExprList],
    new CompletionProvider[CompletionParameters] {
      override def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                                  result: CompletionResultSet): Unit = {
        val element = positionFromParameters(parameters)
        extractReference[ScArgumentExprList](element).foreach { case (referenceExpression, args) =>
          val elements = functionArguments(args, referenceExpression)
          result.addAllElements(elements)
        }
      }
    }
  )

  /*
    call {ref}
    if expected type is function, so we can suggest anonymous function creation
   */
  extend(
    identifierWithParentsPattern(classOf[ScReferenceExpression], classOf[ScBlockExpr], classOf[ScArgumentExprList], classOf[ScMethodCall]),
    new CompletionProvider[CompletionParameters] {
      override def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                                  result: CompletionResultSet): Unit = {
        val element = positionFromParameters(parameters)
        val referenceExpression = element.getContext.asInstanceOf[ScReferenceExpression]
        val block = referenceExpression.getContext.asInstanceOf[ScBlockExpr]
        val args = block.getContext.asInstanceOf[ScArgumentExprList]

        val elements = functionArguments(args, referenceExpression)
        result.addAllElements(elements)
      }
    }
  )

  /*
    call(exprs, ref, exprs)
   */
  extend(classOf[ScArgumentExprList])

  /*
    if (ref) expr
    if (expr) ref
    if (expr) expr else ref
   */
  extend(
    classOf[ScIf],
    new CompletionProvider[CompletionParameters] {
      override def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                                  result: CompletionResultSet): Unit = {
        val element = positionFromParameters(parameters)
        extractReference[ScIf](element).foreach { case (ref, ifStmt) =>
          if (ifStmt.condition.getOrElse(null: ScExpression) == ref)
            acceptTypes(ref.expectedTypes(), ref.getVariants, result, parameters.getInvocationCount > 1, ScalaCompletionUtil.hasNoQualifier(ref), parameters.getOriginalPosition)(element)
          else acceptTypes(ifStmt.expectedTypes(), ref.getVariants, result, parameters.getInvocationCount > 1, ScalaCompletionUtil.hasNoQualifier(ref), parameters.getOriginalPosition)(element)
        }
      }
    }
  )

  /*
    while (ref) expr
    while (expr) ref
   */
  extend(
    classOf[ScWhile],
    new CompletionProvider[CompletionParameters] {
      override def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                                  result: CompletionResultSet): Unit = {
        val element = positionFromParameters(parameters)
        extractReference[ScWhile](element).foreach { case (ref, whileStmt) =>
          if (whileStmt.condition.getOrElse(null: ScExpression) == ref)
            acceptTypes(ref.expectedTypes(), ref.getVariants, result, parameters.getInvocationCount > 1, ScalaCompletionUtil.hasNoQualifier(ref), parameters.getOriginalPosition)(element)
          else acceptTypes(ref.expectedTypes(), ref.getVariants, result, parameters.getInvocationCount > 1, ScalaCompletionUtil.hasNoQualifier(ref), parameters.getOriginalPosition)(element)
        }
      }
    }
  )

  /*
   do expr while (ref)
   do ref while (expr)
  */
  extend(
    classOf[ScDo],
    new CompletionProvider[CompletionParameters] {
      override def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                                  result: CompletionResultSet): Unit = {
        val element = positionFromParameters(parameters)
        extractReference[ScDo](element).foreach { case (ref, doStmt) =>
          if (doStmt.condition.getOrElse(null: ScExpression) == ref)
            acceptTypes(ref.expectedTypes(), ref.getVariants, result, parameters.getInvocationCount > 1, ScalaCompletionUtil.hasNoQualifier(ref), parameters.getOriginalPosition)(element)
          else acceptTypes(ref.expectedTypes(), ref.getVariants, result, parameters.getInvocationCount > 1, ScalaCompletionUtil.hasNoQualifier(ref), parameters.getOriginalPosition)(element)
        }
      }
    }
  )

  /*
    expr op ref
    expr ref name
    ref op expr
   */
  extend(
    classOf[ScInfixExpr],
    new CompletionProvider[CompletionParameters] {
      override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {
        val element = positionFromParameters(parameters)
        extractReference[ScInfixExpr](element).foreach { case (ref, infix) =>
          val typez: ArrayBuffer[ScType] = new ArrayBuffer[ScType]
          if (infix.left == ref) {
            if (infix.isRightAssoc) {
              typez ++= ref.expectedTypes()
            }
          } else if (infix.right == ref) {
            if (infix.isLeftAssoc) {
              typez ++= ref.expectedTypes()
            }
          }
          acceptTypes(typez, ref.getVariants, result, parameters.getInvocationCount > 1,
            ScalaCompletionUtil.hasNoQualifier(ref), parameters.getOriginalPosition)(element)
        }
      }
    }
  )

  /*
    inside try block according to expected type
   */
  extend(classOf[ScTry])

  /*
   inside block expression according to expected type
   */
  extend(classOf[ScBlockExpr])

  /*
   inside finally block
   */
  extend(classOf[ScFinallyBlock])

  /*
   inside anonymous function
   */
  extend(classOf[ScFunctionExpr])

  /*
   for function definitions
   */
  extend(classOf[ScFunctionDefinition])

  /*
   for default parameters
   */
  extend(classOf[ScParameter])

  private def extend[T <: ScalaPsiElement](clazz: Class[T]): Unit =
    extend(clazz, new ScalaSmartCompletionProvider)

  private def extend(clazz: Class[_ <: ScalaPsiElement],
                     provider: CompletionProvider[CompletionParameters]): Unit =
    extend(superParentPattern(clazz), provider)

  private def extend(pattern: ElementPattern[_ <: PsiElement],
                     provider: CompletionProvider[CompletionParameters]): Unit =
    extend(CompletionType.SMART, pattern, provider)
}

object ScalaSmartCompletionContributor {

  private class ScalaSmartCompletionProvider[T <: PsiElement] extends CompletionProvider[CompletionParameters] {

    override def addCompletions(parameters: CompletionParameters,
                                context: ProcessingContext,
                                resultSet: CompletionResultSet): Unit =
      positionFromParameters(parameters) match {
        case place@Reference(reference) => acceptTypes(
          reference.expectedTypes(),
          reference.getVariants,
          resultSet,
          parameters.getInvocationCount > 1,
          ScalaCompletionUtil.hasNoQualifier(reference),
          parameters.getOriginalPosition
        )(place)
        case _ =>
      }
  }

  private[completion] object Reference {
    def unapply(element: PsiElement): Option[ScReferenceExpression] = element.getContext match {
      case reference: ScReferenceExpression =>
        val result = reference.getContext match {
          case newReference: ScReferenceExpression => newReference
          case _ => reference
        }

        Some(result)
      case _ => None
    }
  }

  private def extractReference[T <: PsiElement](element: PsiElement): Option[(ScReferenceExpression, T)] =
    Option(element).collect {
      case Reference(r) => (r, r.getContext.asInstanceOf[T])
    }

  private def superParentPattern(clazz: Class[_ <: ScalaPsiElement]) =
    identifierWithParentsPattern(classOf[ScReferenceExpression], clazz) ||
      identifierWithParentsPattern(classOf[ScReferenceExpression], classOf[ScReferenceExpression], clazz)

  private def acceptTypes(typez: Iterable[ScType], variants: Array[Object],
                          result: CompletionResultSet,
                          secondCompletion: Boolean,
                          completeThis: Boolean,
                          originalPlace: PsiElement)
                         (implicit place: PsiElement): Unit = {
    implicit val project: Project = place.getProject

    if (typez.isEmpty || typez.forall(_ == Nothing)) return

    def applyVariant(variant: Object, checkForSecondCompletion: Boolean = false): Unit = {

      def handleVariant(scalaLookupItem: ScalaLookupItem, chainVariant: Boolean = false): Unit = {
        val elemToAdd = variant.asInstanceOf[LookupElement]
        if (isAccessible(scalaLookupItem) && !scalaLookupItem.isNamedParameterOrAssignment) {
          def checkType(_tp: ScType, _subst: ScSubstitutor, chainCompletion: Boolean, etaExpanded: Boolean = false): Boolean = {
            val tp = _subst(_tp)
            var elementAdded = false
            val scType = scalaLookupItem.substitutor(tp)
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
                    val variant = new ScalaChainLookupElement(
                      result.createLookupElement(),
                      scalaLookupItem
                    )

                    applyVariant(variant)
                  }
                }
              }
              processor.processType(scalaLookupItem.substitutor(_tp), place)
              processor.candidatesS
            }
            elementAdded
          }

          scalaLookupItem.getPsiElement match {
            case clazz: PsiClass if isExcluded(clazz) =>
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
              val infer = if (chainVariant) ScSubstitutor.empty else ScalaPsiUtil.undefineMethodTypeParams(fun)
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
              val infer = if (chainVariant) ScSubstitutor.empty else ScalaPsiUtil.undefineMethodTypeParams(method)
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
        case ch: ScalaChainLookupElement => handleVariant(ch.getDelegate, chainVariant = true)
        case _ =>
      }
    }

    place.getContext match {
      case ref: ScReferenceExpression if ref.smartQualifier.isEmpty =>
        //enum and factory methods
        val iterator = typez.iterator
        while (iterator.hasNext) {
          val tp = iterator.next()

          // todo unify with SbtCompletionContributor.collectAndApplyVariants
          def checkObject(containingClass: ScObject): Unit =
            if (isAccessible(containingClass) && ScalaPsiUtil.hasStablePath(containingClass)) {
              containingClass.membersWithSynthetic.flatMap {
                case function: ScFunction => Seq(function)
                case v: ScValueOrVariable => v.declaredElements
                case obj: ScObject => Seq(obj)
                case _ => Seq.empty
              }.map {
                createLookupElementWithPrefix(_, containingClass)
              }.foreach {
                applyVariant(_)
              }
            }

          def checkTypeProjection(tp: ScType): Unit = {
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
          def checkType(tp: ScType): Unit = {
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
              case Some(containingClass: PsiClass) if isAccessible(containingClass) =>
                // todo unify with SbtCompletionContributor
                for {
                  member <- containingClass.getAllMethods ++ containingClass.getFields
                  if member.hasModifierProperty(PsiModifier.STATIC) &&
                    isAccessible(member)

                  variant = createLookupElementWithPrefix(member, containingClass)
                } applyVariant(variant)
              case _ => checkTypeProjection(tp)
            }
          }

          checkType(tp)
        }
        variants.foreach(applyVariant(_, checkForSecondCompletion = true))
        if (typez.exists(_.equiv(Boolean))) {
          ScalaKeywordLookupItem.addFor(result, ScalaKeyword.FALSE, ScalaKeyword.TRUE)
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

  private[this] def isAccessible(item: ScalaLookupItem)
                                (implicit place: PsiElement): Boolean = ScalaPsiUtil.nameContext(item.getPsiElement) match {
    case member: ScMember => isAccessible(member)
    case _ => true
  }

  private[this] def isAccessible(member: PsiMember)
                                (implicit place: PsiElement): Boolean =
    ResolveUtils.isAccessible(member, place, forCompletion = true)

  private def functionArguments(args: ScArgumentExprList,
                                reference: ScReferenceExpression) = {
    val isBraceArgs = args.isBraceArgs

    reference.expectedTypes().collect {
      case FunctionType(_, types) => types
    }.map {
      case Seq(TupleType(types)) if isBraceArgs => types
      case types => types
    }.map {
      createLookupElement(_, new AnonymousFunctionTextBuilder(isBraceArgs))(reference.getProject)
    }.asJava
  }

  private[this] def createLookupElement(params: Iterable[ScType],
                                        builder: AnonymousFunctionTextBuilder)
                                       (implicit project: Project) =
    LookupElementBuilder.create("").withRenderer {
      new AnonymousFunctionElementRenderer(params, builder)
    }.withInsertHandler {
      new AnonymousFunctionInsertHandler(params, builder)
    }.withAutoCompletionPolicy {
      import AutoCompletionPolicy._
      if (ApplicationManager.getApplication.isUnitTestMode) ALWAYS_AUTOCOMPLETE
      else NEVER_AUTOCOMPLETE
    }

  import ScalaPsiUtil.functionArrow

  private class AnonymousFunctionElementRenderer(params: Iterable[ScType],
                                                 builder: AnonymousFunctionTextBuilder)
                                                (implicit project: Project) extends LookupElementRenderer[LookupElement] {

    private val presentableParams = for {
      parameterType <- params
      simplifiedType = parameterType.removeAbstracts
    } yield (simplifiedType, simplifiedType.presentableText(TypePresentationContext.emptyContext))

    override def renderElement(element: LookupElement,
                               presentation: LookupElementPresentation): Unit = {
      val itemText = builder(presentableParams)

      def isEnoughSpaceFor(text: String): Boolean = text.length < 100

      @tailrec
      def collapseLastParams(index: Int): String = index - 1 match {
        case 0 => s"... $functionArrow "
        case newIndex =>
          val shortened = builder(presentableParams.take(newIndex), paramsSuffix = ", ...")

          if (isEnoughSpaceFor(shortened)) shortened
          else collapseLastParams(index - 1)
      }

      val text =
        if (isEnoughSpaceFor(itemText)) itemText
        else collapseLastParams(presentableParams.size)

      presentation.setItemText(text)
      presentation.setIcon(Icons.LAMBDA)
    }
  }

  private class AnonymousFunctionInsertHandler(params: Iterable[ScType],
                                               builder: AnonymousFunctionTextBuilder) extends InsertHandler[LookupElement] {

    override def handleInsert(context: InsertionContext, lookupElement: LookupElement): Unit = {
      val abstracts = {
        val result = mutable.HashSet.empty[ScAbstractType]
        params.foreach {
          _.visitRecursively {
            case abstractType: ScAbstractType => result += abstractType
            case _ =>
          }
        }
        result.toSet
      }

      val InsertionContextExt(editor, document, _, project) = context
      context.setAddCompletionChar(false)

      val text = this.builder {
        params.map { `type` =>
          `type` -> `type`.canonicalText
        }
      }(project)

      document.insertString(editor.getCaretModel.getOffset, text)
      val documentManager = PsiDocumentManager.getInstance(project)
      documentManager.commitDocument(document)
      val file = documentManager.getPsiFile(document)
      val startOffset = context.getStartOffset
      val endOffset = startOffset + text.length()
      val commonParent = PsiTreeUtil.findCommonParent(file.findElementAt(startOffset), file.findElementAt(endOffset - 1))

      ScalaPsiUtil.adjustTypes(commonParent)

      val builder = TemplateBuilderFactory.getInstance()
        .createTemplateBuilder(commonParent)
        .asInstanceOf[TemplateBuilderImpl]

      val abstractNames = abstracts.map { `type` =>
        val name = `type`.typeParameter.name
        name + TypePresentation.ABSTRACT_TYPE_POSTFIX -> (`type`, name)
      }.toMap

      def seekAbstracts(te: ScTypeElement)(implicit tpc: TypePresentationContext): Unit = {
        val visitor = new ScalaRecursiveElementVisitor {

          override def visitSimpleTypeElement(simple: ScSimpleTypeElement): Unit = for {
            reference <- simple.reference
            refName = reference.refName

            (abstractType, name) <- abstractNames.get(refName)
            value = abstractType.simplifyType match {
              case simplifiedType if simplifiedType.isAny || simplifiedType.isNothing => name
              case simplifiedType => simplifiedType.presentableText
            }
          } builder.replaceElement(simple, refName, new ConstantNode(value), false)
        }

        te.accept(visitor)
      }

      implicit val tpc: TypePresentationContext = TypePresentationContext(commonParent)
      commonParent match {
        case f: ScFunctionExpr =>
          for (parameter <- f.parameters) {
            parameter.typeElement.foreach(seekAbstracts(_))
            builder.replaceElement(parameter.nameId, parameter.name)
          }
          f.result.foreach {
            case qMarks: ScReferenceExpression if qMarks.refName == NotImplementedError =>
              builder.replaceElement(qMarks.nameId, qMarks.refName)
            case _ =>
          }
        case c: ScCaseClause => c.pattern match {
          case Some(pattern) =>
            for (binding <- pattern.bindings) {
              binding match {
                case ScTypedPattern(typeElement) => seekAbstracts(typeElement)
                case _ =>
              }

              builder.replaceElement(binding.nameId, binding.name)
            }
          case _ =>
        }
      }

      CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(commonParent)

      val template = builder.buildTemplate()
      for {
        (name, (_, actualName)) <- abstractNames
      } template.addVariable(name, actualName, actualName, false)

      document.deleteString(commonParent.getTextRange.getStartOffset, commonParent.getTextRange.getEndOffset)
      TemplateManager.getInstance(project).startTemplate(editor, template)
    }
  }

  private[this] class AnonymousFunctionTextBuilder(braceArgs: Boolean) {

    type Parameter = (ScType, String)

    def apply(types: Iterable[Parameter], paramsSuffix: String = "")
             (implicit project: Project): String =
      createBuffer
        .append(suggestedParameters(types))
        .append(paramsSuffix)
        .append(" ")
        .append(functionArrow)
        .append(" ")
        .append(NotImplementedError)
        .toString

    private def createBuffer = new StringBuilder() match {
      case buffer if braceArgs => buffer.append(ScalaKeyword.CASE).append(" ")
      case buffer => buffer
    }

    private def suggestedParameters(types: Iterable[Parameter]) = {
      import Model._

      val suggester = new NameSuggester.UniqueNameSuggester("x")
      types.map {
        case (scType, text) => suggester(scType) + ": " + text
      }.commaSeparated(if (types.size != 1 || !braceArgs) Parentheses else None)
    }
  }

}