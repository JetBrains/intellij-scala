package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.JavaCompletionFeatures.GLOBAL_MEMBER_NAME
import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.openapi.actionSystem.{ActionManager, IdeActions}
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.ResolveState.initial
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.ProcessingContext
import gnu.trove.THashSet
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.LookupElementManager.getLookupElement
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.nameContext
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.implicits.{CollectImplicitsProcessor, ScImplicitlyConvertible}
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScThisType
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult, StdKinds}

import scala.collection.{JavaConverters, mutable}

/**
  * @author Alexander Podkhalyuzin
  */
class ScalaGlobalMembersCompletionContributor extends ScalaCompletionContributor {

  import ScalaGlobalMembersCompletionContributor._

  extend(CompletionType.BASIC, psiElement, new CompletionProvider[CompletionParameters]() {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      if (parameters.getInvocationCount < 2) return
      val position: PsiElement = positionFromParameters(parameters)
      if (!ScalaPsiUtil.fileContext(position).isInstanceOf[ScalaFile]) return

      position.getContext match {
        case reference: ScReferenceExpression =>
          val maybeQualifier = reference.qualifier.orElse {
            Option(reference.getContext).collect {
              case expr: ScSugarCallExpr if expr.operation == reference => expr.getBaseExpr
            }
          }

          val file = parameters.getOriginalFile
          maybeQualifier match {
            case Some(qualifier) =>
              qualifier.getTypeWithoutImplicits().foreach {
                completeImplicits(reference, result, file, _)
              }
            case _ =>
              if (result.getPrefixMatcher.getPrefix != "") {
                complete(reference, result, file, parameters.getInvocationCount)
              }
          }
        case _ =>
      }
    }
  })
}

object ScalaGlobalMembersCompletionContributor {

  import JavaConverters._

  private def isStatic(element: PsiNamedElement): Boolean = {
    nameContext(element) match {
      case member: PsiMember => isStatic(element, member.containingClass)
    }
  }

  private def isStatic(element: PsiNamedElement, containingClass: PsiClass): Boolean = {
    nameContext(element) match {
      case member: PsiMember =>
        if (containingClass == null) return false
        val qualifiedName = containingClass.qualifiedName + "." + element.name
        for (excluded <- CodeInsightSettings.getInstance.EXCLUDED_PACKAGES) {
          if (qualifiedName == excluded || qualifiedName.startsWith(excluded + ".")) {
            return false
          }
        }
        containingClass match {
          case o: ScObject if o.isStatic =>
            // filter out type class instances, such as scala.math.Numeric.String, to avoid too many results.
            !o.hasModifierProperty("implicit")
          case _: ScTypeDefinition => false
          case _ => member.hasModifierProperty("static")
        }
    }
  }

  private[this] def implicitElements(reference: ScReferenceExpression): Iterable[ScMember] = {
    triggerFeature()

    StubIndex.getElements(
      ScalaIndexKeys.IMPLICITS_KEY,
      "implicit",
      reference.getContainingFile.getProject,
      reference.getResolveScope,
      classOf[ScMember]
    ).asScala
  }


  private[this] def implicitProcessor(reference: ScReferenceExpression): CollectImplicitsProcessor = {
    val processor = new CollectImplicitsProcessor(reference, true)
    val processedObjects: mutable.HashSet[String] = mutable.HashSet.empty

    val names = Set(
      "scala.collection.convert.DecorateAsJava",
      "scala.collection.convert.DecorateAsScala",
      "scala.collection.convert.WrapAsScala",
      "scala.collection.convert.WrapAsJava"
    )

    def processElements(elements: Seq[PsiNamedElement]): Unit = elements
      .filter(isStatic)
      .foreach(processor.execute(_, initial()))

    def processInheritors(inheritors: Iterable[PsiClass]): Unit = {
      val maybeObject = inheritors.collectFirst {
        case o: ScObject if o.isStatic => o
      }.filter { o =>
        processedObjects.add(o.qualifiedName)
      }

      val maybeType = maybeObject.flatMap(_.getType().toOption)
      maybeType.foreach(processor.processType(_, reference))
    }

    implicitElements(reference).collect {
      case v: ScValue => (v, () => v.declaredElements)
      case f: ScFunction => (f, () => Seq(f))
      case c: ScClass => (c, () => c.getSyntheticImplicitMethod.toSeq)
    }.flatMap {
      case (member, elements) => Option(member.containingClass).map((_, elements))
    }.foreach {
      case (_: ScObject, elements) =>
        processElements(elements())
      case (definition, _) if names(definition.qualifiedName) =>
        processInheritors(ClassInheritorsSearch.search(definition, false).asScala)
      case _ =>
    }

    processor
  }

  private def completeImplicits(ref: ScReferenceExpression, result: CompletionResultSet, originalFile: PsiFile,
                                originalType: ScType) {
    val elements = elementsSet(ref)

    implicitProcessor(ref).candidates.toSeq.flatMap {
      ScImplicitlyConvertible.forMap(ref, _, originalType)
    }.flatMap { result =>
      val processor = new CompletionProcessor(StdKinds.methodRef, ref)
      processor.processType(result.resultType, ref)

      processor.candidates.map { candidate =>
        getLookupElement(
          candidate,
          isClassName = true,
          shouldImport = shouldImport(candidate.getElement, elements, ref.getContainingFile, originalFile)).head
      }.map {
        (_, result.resolveResult)
      }
    }.map {
      case (lookup, resolveResult) =>
        lookup.usedImportStaticQuickfix = true

        val (element, elementObject) = adaptResolveResult(resolveResult)
        lookup.elementToImport = Option(element)
        lookup.objectOfElementToImport = elementObject

        lookup
    }.foreach(result.addElement)
  }

  private def complete(ref: ScReferenceExpression, result: CompletionResultSet, originalFile: PsiFile, invocationCount: Int): Unit = {
    triggerFeature()

    val matcher: PrefixMatcher = result.getPrefixMatcher
    val scope: GlobalSearchScope = ref.getResolveScope
    val file = ref.getContainingFile

    var hintShown: Boolean = false

    def showHint(shouldImport: Boolean): Unit =
      if (!hintShown && !shouldImport && CompletionService.getCompletionService.getAdvertisementText == null) {
        val action = ActionManager.getInstance.getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS)
        Option(KeymapUtil.getFirstKeyboardShortcutText(action)).foreach { shortcut =>
          result.addLookupAdvertisement(s"To import a method statically, press $shortcut")
        }
        hintShown = true
      }

    val elemsSet = elementsSet(ref)
    val namesCache = ScalaShortNamesCacheManager.getInstance(ref.getProject)

    def isAccessible(member: PsiMember, containingClass: PsiClass): Boolean = invocationCount >= 3 ||
      (ResolveUtils.isAccessible(member, ref, forCompletion = true) && ResolveUtils.isAccessible(containingClass, ref, forCompletion = true))

    def inheritors(clazz: PsiClass) =
      ClassInheritorsSearch.search(clazz, scope, true).toArray(PsiClass.EMPTY_ARRAY).toSeq

    (namesCache.getAllMethodNames ++ namesCache.getAllJavaMethodNames).filter {
      matcher.prefixMatches
    }.foreach { methodName =>
      val classes = new THashSet[PsiClass]

      namesCache.getMethodsByName(methodName, scope).foreach { method =>
        Option(method.containingClass).toSeq.flatMap { containingClass =>
          Seq(containingClass) ++ (method match {
            case _: ScFunction => inheritors(containingClass)
            case _ => Seq.empty
          })
        }.filter(isStatic(method, _))
          .filter { containingClass =>
            classes.add(containingClass) && isAccessible(method, containingClass)
          }.foreach { containingClass =>
          val shouldImport = ScalaGlobalMembersCompletionContributor.shouldImport(method, elemsSet, file, originalFile)
          showHint(shouldImport)

          val overloads = containingClass match {
            case o: ScObject => o.functionsByName(methodName)
            case _ => containingClass.getAllMethods.toSeq.filter {
              _.name == methodName
            }
          }

          val overload = overloads match {
            case Seq() => None
            case Seq(_) => Some(method, false)
            case Seq(first, second, _*) =>
              Some(if (first.getParameterList.getParametersCount != 0) first else second, true)
          }

          overload.map {
            case (m, overloaded) =>
              createLookupElement(m, containingClass, shouldImport, overloaded = overloaded)
          }.foreach {
            result.addElement
          }
        }
      }
    }

    def createLookups(field: PsiMember, namedElement: PsiNamedElement, containingClasses: Iterable[PsiClass]) =
      containingClasses.filter(isAccessible(field, _))
        .map { containingClass =>
          val shouldImport = ScalaGlobalMembersCompletionContributor.shouldImport(namedElement, elemsSet, file, originalFile)
          showHint(shouldImport)
          createLookupElement(namedElement, containingClass, shouldImport)
        }.foreach {
        result.addElement
      }

    namesCache.getAllFieldNames
      .filter(matcher.prefixMatches)
      .flatMap(namesCache.getFieldsByName(_, scope))
      .filter(isStatic)
      .foreach { field =>
        createLookups(field, field, Option(field.containingClass))
      }

    namesCache.getAllScalaFieldNames
      .filter(matcher.prefixMatches)
      .foreach { fieldName =>
        namesCache.getScalaFieldsByName(fieldName, scope).collect {
          case v: ScValueOrVariable => v
        }.foreach { field =>
          field.declaredElements.find {
            _.name == fieldName
          }.foreach { namedElement =>
            val classes = Option(field.containingClass).toSeq.flatMap { containingClass =>
              Seq(containingClass) ++ inheritors(containingClass)
            }.filter {
              isStatic(namedElement, _)
            }

            createLookups(field, namedElement, classes)
          }
        }
      }
  }

  private[this] def triggerFeature(): Unit =
    FeatureUsageTracker.getInstance.triggerFeatureUsed(GLOBAL_MEMBER_NAME)

  private def adaptResolveResult(resolveResult: ScalaResolveResult) = {
    val ScalaResolveResult(element, substitutor) = resolveResult

    val elementObject = Option(element)
      .map(nameContext)
      .collect {
        case member: ScMember => member
      }.flatMap { member =>
      Option(member.containingClass)
    }.collect {
      case c: ScClass => c
      case t: ScTrait => t
    }.map(ScThisType)
      .map(substitutor.subst)
      .flatMap(_.extractClass)
      .collect {
        case o: ScObject => o
      }

    (element, elementObject)
  }

  private def shouldImport(element: PsiNamedElement, elements: Set[PsiNamedElement],
                           file: PsiFile, originalFile: PsiFile): Boolean = {
    def qualifiedName(element: PsiNamedElement, file: PsiFile) =
      Option(element)
        .filter(_.getContainingFile == file)
        .map(nameContext)
        .collect {
          case member: PsiMember => member
        }.flatMap { member =>
        Option(member.containingClass)
      }.flatMap { clazz =>
        Option(clazz.qualifiedName)
      }

    val contains = if (element.getContainingFile == originalFile) {
      //complex logic to detect static methods in same file, which we shouldn't import
      qualifiedName(element, originalFile).exists { name =>
        elements.filter(_.name == element.name)
          .flatMap(qualifiedName(_, file))
          .contains(name)
      }
    } else {
      elements.contains(element)
    }

    !contains
  }

  private def elementsSet(reference: ScReferenceExpression) = {
    reference.getVariants(implicits = false, filterNotNamedVariants = false).collect {
      case ScalaLookupItem(element: PsiNamedElement) => element
      case element: PsiNamedElement => element
    }.toSet
  }

  private def createLookupElement(member: PsiNamedElement, clazz: PsiClass, shouldImport: Boolean,
                                  overloaded: Boolean = false): LookupElement =
    getLookupElement(new ScalaResolveResult(member), isClassName = true,
      isOverloadedForClassName = overloaded, shouldImport = shouldImport, containingClass = Some(clazz)).head
}