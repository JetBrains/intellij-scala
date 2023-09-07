package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup._
import com.intellij.codeInsight.template._
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.NotImplementedError
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.completion.handlers.ScalaConstructorInsertHandler
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
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{DesignatorOwner, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypePresentation
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult, StdKinds}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.jdk.CollectionConverters._

final class ScalaSmartCompletionContributor extends ScalaCompletionContributor {

  import ScalaSmartCompletionContributor._

  /*
    ref = expr
    expr = ref
   */
  extendSmartAndBasic(
    classOf[ScAssignment],
    new ScalaSmartCompletionProvider {
      override def addCompletions(implicit parameters: CompletionParameters,
                                  result: CompletionResultSet,
                                  place: PsiElement): Unit =
        extractReference[ScAssignment](place).foreach { case (ref, assign) =>
          if (assign.rightExpression.contains(ref)) {
            assign.leftExpression match {
              case _: ScMethodCall => //todo: it's update method
              case _: ScExpression =>
                //we can expect that the type is same for left and right parts.
                acceptTypes(ref.expectedTypes(), ref)
            }
          } else {
            //so it's left expression
            //todo: if right expression exists?
          }
        }
    }
  )

  /*
    val x: Type = ref
    var y: Type = ref
   */
  extendSmartAndBasic(
    superParentPattern(classOf[ScPatternDefinition]) || superParentPattern(classOf[ScVariableDefinition]),
    new ScalaSmartCompletionProvider {
      override def addCompletions(implicit parameters: CompletionParameters,
                                  result: CompletionResultSet,
                                  place: PsiElement): Unit =
        extractReference[PsiElement](place).foreach { case (ref, _) =>
          acceptTypes(ref.expectedType(), ref)
        }
    })

  /*
    return ref
   */
  extendSmartAndBasic(
    classOf[ScReturn],
    new ScalaSmartCompletionProvider {
      override def addCompletions(implicit parameters: CompletionParameters, result: CompletionResultSet, place: PsiElement): Unit =
        extractReference[ScReturn](place).foreach { case (ref, _) =>
          PsiTreeUtil.getContextOfType(ref, classOf[ScFunction]) match {
            case null =>
            case fun => acceptTypes(Seq[ScType](fun.returnType.getOrAny), ref)
          }
        }
    }
  )

  /*
    call(exprs, ref, exprs)
    if expected type is function, so we can suggest anonymous function creation
   */
  extendSmart(
    classOf[ScArgumentExprList],
    new ScalaSmartCompletionProvider {
      override def addCompletions(implicit parameters: CompletionParameters,
                                  result: CompletionResultSet,
                                  place: PsiElement): Unit =
        extractReference[ScArgumentExprList](place).foreach { case (referenceExpression, args) =>
          val elements = functionArguments(args, referenceExpression)
          result.addAllElements(elements)
        }
    }
  )

  /*
    call {ref}
    if expected type is function, so we can suggest anonymous function creation
   */
  extendSmart(
    identifierWithParentsPattern(classOf[ScReferenceExpression], classOf[ScBlockExpr], classOf[ScArgumentExprList], classOf[ScMethodCall]),
    new ScalaSmartCompletionProvider {
      override def addCompletions(implicit parameters: CompletionParameters,
                                  result: CompletionResultSet,
                                  place: PsiElement): Unit = {
        val referenceExpression = place.getContext.asInstanceOf[ScReferenceExpression]
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
  extend(classOf[ScArgumentExprList], onlySmart = false)

  /*
    if (ref) expr
    if (expr) ref
    if (expr) expr else ref
   */
  extendSmartAndBasic(
    classOf[ScIf],
    new ScalaSmartCompletionProvider {
      override def addCompletions(implicit parameters: CompletionParameters,
                                  result: CompletionResultSet,
                                  place: PsiElement): Unit =
        extractReference[ScIf](place).foreach { case (ref, ifStmt) =>
          val expr = if (ifStmt.condition.contains(ref)) ref else ifStmt
          acceptTypes(expr.expectedTypes(), ref)
        }
    }
  )

  /*
    case expr
   */
  extendSmartAndBasic(
    psiElement.inside(classOf[ScCaseClause]),
    new ScalaSmartCompletionProvider {
      override def addCompletions(implicit parameters: CompletionParameters,
                                  result: CompletionResultSet,
                                  place: PsiElement): Unit = for {
        ref <- place.findContextOfType(classOf[ScReferenceExpression])
        caseClause <- ref.findContextOfType(classOf[ScCaseClause])
        if caseClause.pattern.contains(ref.getContext)
      } acceptTypes(caseClause.pattern.flatMap(_.expectedType), ref)
    }
  )

  /*
    while (ref) expr
    while (expr) ref
   */
  extendSmart(
    classOf[ScWhile],
    new ScalaSmartCompletionProvider {
      override def addCompletions(implicit parameters: CompletionParameters,
                                  result: CompletionResultSet,
                                  place: PsiElement): Unit =
        extractReference[ScWhile](place).foreach { case (ref, _) =>
          acceptTypes(ref.expectedTypes(), ref)
        }
    }
  )

  /*
   do expr while (ref)
   do ref while (expr)
  */
  extendSmart(
    classOf[ScDo],
    new ScalaSmartCompletionProvider {
      override def addCompletions(implicit parameters: CompletionParameters,
                                  result: CompletionResultSet,
                                  place: PsiElement): Unit =
        extractReference[ScDo](place).foreach { case (ref, _) =>
          acceptTypes(ref.expectedTypes(), ref)
        }
    }
  )

  /*
    expr op ref
    expr ref name
    ref op expr
   */
  extendSmartAndBasic(
    classOf[ScInfixExpr],
    new ScalaSmartCompletionProvider {
      override def addCompletions(implicit parameters: CompletionParameters,
                                  result: CompletionResultSet,
                                  place: PsiElement): Unit =
        extractReference[ScInfixExpr](place).foreach { case (ref, infix) =>
          if (infix.left == ref && infix.isRightAssoc || infix.right == ref && infix.isLeftAssoc) {
            acceptTypes(ref.expectedTypes(), ref)
          }
        }
    }
  )

  /*
    inside try block according to expected type
   */
  extend(classOf[ScTry], onlySmart = false)

  /*
   inside block expression according to expected type
   */
  extend(classOf[ScBlockExpr], onlySmart = false)

  /*
   inside finally block
   */
  extend(classOf[ScFinallyBlock], onlySmart = true)

  /*
   inside anonymous function
   */
  extend(classOf[ScFunctionExpr], onlySmart = true)

  /*
   for function definitions
   */
  extend(classOf[ScFunctionDefinition], onlySmart = false)

  /*
   for default parameters
   */
  extend(classOf[ScParameter], onlySmart = false)

  private def extend[T <: ScalaPsiElement](clazz: Class[T], onlySmart: Boolean): Unit = {
    val provider = new ScalaSmartCompletionProvider {
      override def addCompletions(implicit parameters: CompletionParameters,
                                  resultSet: CompletionResultSet,
                                  place: PsiElement): Unit =
        place match {
          case Reference(reference) => acceptTypes(reference.expectedTypes(), reference)
          case _ =>
        }
    }

    if (onlySmart) extendSmart(clazz, provider)
    else extendSmartAndBasic(clazz, provider)
  }

  private def extendSmart(clazz: Class[_ <: ScalaPsiElement],
                          provider: CompletionProvider[CompletionParameters]): Unit =
    extendSmart(superParentPattern(clazz), provider)

  private def extendSmartAndBasic(clazz: Class[_ <: ScalaPsiElement],
                                  provider: CompletionProvider[CompletionParameters]): Unit =
    extendSmartAndBasic(superParentPattern(clazz), provider)

  private def extendSmart(pattern: ElementPattern[_ <: PsiElement],
                          provider: CompletionProvider[CompletionParameters]): Unit =
    extend(CompletionType.SMART, pattern, provider)

  private def extendSmartAndBasic(pattern: ElementPattern[_ <: PsiElement],
                                  provider: CompletionProvider[CompletionParameters]): Unit = {
    extendSmart(pattern, provider)
    extend(CompletionType.BASIC, pattern, provider) // SCL-19749
  }
}

object ScalaSmartCompletionContributor {

  private abstract class ScalaSmartCompletionProvider extends CompletionProvider[CompletionParameters] {
    final override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {
      val place = positionFromParameters(parameters)
      addCompletions(parameters, result, place)
    }

    protected def addCompletions(implicit parameters: CompletionParameters, result: CompletionResultSet, place: PsiElement): Unit
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

  private def acceptTypes(types: Iterable[ScType], ref: ScReferenceExpression)
                         (implicit parameters: CompletionParameters, result: CompletionResultSet, place: PsiElement): Unit =
    acceptTypes(
      types,
      ref.getVariants,
      result,
      parameters.getInvocationCount > 1,
      ScalaCompletionUtil.hasNoQualifier(ref),
      parameters.getOriginalPosition,
      parameters.getCompletionType == CompletionType.SMART
    )

  private def acceptTypes(typez: Iterable[ScType], variants: Array[Object],
                          result: CompletionResultSet,
                          secondCompletion: Boolean,
                          completeThis: Boolean,
                          originalPlace: PsiElement,
                          isSmart: Boolean)
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

          def checkTyped(typed: PsiNamedElement with Typeable): Unit = {
            if (!PsiTreeUtil.isContextAncestor(typed.nameContext, place, false) &&
              (originalPlace == null || !PsiTreeUtil.isContextAncestor(typed.nameContext, originalPlace, false)))
              for (tt <- typed.`type`()) checkType(tt, ScSubstitutor.empty, checkForSecondCompletion)
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
            case typed: ScTypedDefinition => checkTyped(typed)
            case typed: ScEnumCase => checkTyped(typed)
            // TODO: java classes?
            case typed: ScTypeDefinition => checkTyped(typed)
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
            if (!isSmart) { // SCL-19749
              tp.extractClass match {
                case Some(scEnum: ScEnum) =>
                  if (isAccessible(scEnum)) {
                    val cases = scEnum.cases
                    cases.foreach { enumCase =>
                      applyVariant(createLookupElement(enumCase, scEnum))
                    }
                  }
                case Some(javaEnum@JavaEnum(fields)) if isAccessible(javaEnum) =>
                  fields.foreach(field => applyVariant(createLookupElementWithPrefix(field, javaEnum)))
                case _ =>
                  val valueType = tp.extractDesignatorSingleton.getOrElse(tp)
                  valueType match {
                    case ScProjectionType(DesignatorOwner(enumeration@ScalaEnumeration(vals)), _) if isAccessible(enumeration) =>
                      val applicableVals = vals.filter(v => isAccessible(v) && v.`type`().exists(_.conforms(valueType)))
                      val fields = applicableVals.flatMap(_.declaredElements)
                      fields.foreach(field => applyVariant(createLookupElementWithPrefix(field, enumeration)))
                    case _ =>
                  }
              }
            } else tp.extractClass match {
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

        if (isSmart) {
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
        }
      case _ =>
        if (isSmart) variants.foreach(applyVariant(_, checkForSecondCompletion = true))
    }
  }

  /**
   * @param element lookup element
   * @param context parent class for the `element`
   * @param isInner is `element` inside of `context`
   */
  private[this] def createLookupElement(element: PsiClass, context: PsiClass, isInner: Boolean = true): ScalaLookupItem = {
    val resolveResult = new ScalaResolveResult(element)

    val lookupElement = resolveResult.createLookupElement(
      isClassName = true,
      shouldImport = true,
      containingClass = Option.when(isInner)(context)
    )
    lookupElement.addLookupStrings(context.name + "." + lookupElement.getPsiElement.name)

    val typeParametersEvaluator: (ScType => String) => String = Function.const("")

    lookupElement.setInsertHandler(
      new ScalaConstructorInsertHandler(
        typeParametersEvaluator,
        resolveResult.problems.nonEmpty,
        element.isInterface,
        lookupElement.isRenamed.isDefined,
        lookupElement.prefixCompletion
      )
    )

    lookupElement
  }

  private[this] def isAccessible(item: ScalaLookupItem)
                                (implicit place: PsiElement): Boolean = item.getPsiElement.nameContext match {
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
