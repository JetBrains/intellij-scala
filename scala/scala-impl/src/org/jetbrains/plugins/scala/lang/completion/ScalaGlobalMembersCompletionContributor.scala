package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion._
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.openapi.actionSystem.{ActionManager, IdeActions}
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi._
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.nameContext
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.implicits.CollectImplicitsProcessor
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible.forMap
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaStubsUtil.getClassInheritors
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScThisType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult, StdKinds}

import scala.collection.{JavaConverters, mutable}

/**
  * @author Alexander Podkhalyuzin
  */
class ScalaGlobalMembersCompletionContributor extends ScalaCompletionContributor {

  import ScalaGlobalMembersCompletionContributor._

  extend(
    CompletionType.BASIC,
    PlatformPatterns.psiElement,
    new CompletionProvider[CompletionParameters] {
      def addCompletions(parameters: CompletionParameters,
                         context: ProcessingContext,
                         resultSet: CompletionResultSet): Unit = {
        val invocationCount = parameters.getInvocationCount
        if (invocationCount < 2) return

        positionFromParameters(parameters).getContext match {
          case place: ScReferenceExpression if PsiTreeUtil.getContextOfType(place, classOf[ScalaFile]) != null =>
            val lookups = createLookups(accessAll = invocationCount >= 3)(place, resultSet.getPrefixMatcher, parameters.getOriginalFile)

            if (CompletionService.getCompletionService.getAdvertisementText != null &&
              lookups.exists(!_.shouldImport)) {
              hintString.foreach(resultSet.addLookupAdvertisement)
            }

            import JavaConverters._
            resultSet.addAllElements(lookups.asJava)
          case _ =>
        }
      }
    }
  )
}

object ScalaGlobalMembersCompletionContributor {

  private def createLookups(accessAll: Boolean)
                           (implicit place: ScReferenceExpression,
                            prefixMatcher: PrefixMatcher,
                            originalFile: PsiFile): Iterable[ScalaLookupItem] = {
    implicit def elementsSet: Set[PsiNamedElement] =
      place.completionVariants()
        .map(_.element).toSet

    def triggerFeature(): Unit =
      FeatureUsageTracker.getInstance.triggerFeatureUsed(JavaCompletionFeatures.GLOBAL_MEMBER_NAME)

    findQualifier match {
      case Some(qualifier) =>
        qualifier.getTypeWithoutImplicits() match {
          case Right(originalType) =>
            triggerFeature()
            for {
              candidate <- implicitCandidates
              mapResult <- forMap(place, candidate, originalType).toSeq

              ScalaResolveResult(element, substitutor) = mapResult.resolveResult
              elementObject = adaptResolveResult(element, substitutor)
              item <- completeImplicits(element, elementObject, mapResult.resultType)
            } yield item
          case _ => Iterable.empty
        }
      case _ if prefixMatcher.getPrefix == "" => Iterable.empty
      case _ =>
        triggerFeature()
        complete(accessAll)(prefixMatcher.prefixMatches)
    }
  }

  private def hintString: Option[String] =
    Option(ActionManager.getInstance.getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS)).map { action =>
      "To import a method statically, press " + KeymapUtil.getFirstKeyboardShortcutText(action)
    }

  private[this] def findQualifier(implicit reference: ScReferenceExpression) =
    reference.qualifier.orElse {
      Option(reference.getContext).collect {
        case ScSugarCallExpr(baseExpression, `reference`, _) => baseExpression
      }
    }

  private[this] def isStatic(element: PsiNamedElement): Boolean =
    contextContainingClass(element).exists(isStatic(element, _))

  private[this] def isStatic(element: PsiNamedElement, containingClass: PsiClass): Boolean = {
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

  private[this] def implicitElements(implicit place: ScReferenceExpression) = {
    import ScalaIndexKeys._
    import place.projectContext

    IMPLICITS_KEY.elements("implicit",
      place.resolveScope,
      classOf[ScMember]
    ).collect {
      case v: ScValue => (v, () => v.declaredElements)
      case f: ScFunction => (f, () => Seq(f))
      case c: ScClass => (c, () => c.getSyntheticImplicitMethod.toSeq)
    }
  }

  private[this] def implicitCandidates(implicit place: ScReferenceExpression) = {
    val processor = new CollectImplicitsProcessor(place, true)

    implicitElements.foreach {
      case (member, elements) =>
        member.containingClass match {
          case _: ScObject =>
            elements().filter {
              isStatic
            }.foreach {
              processor.execute(_, ResolveState.initial)
            }
          case definition: ScTemplateDefinition =>
            val processedObjects = mutable.HashSet.empty[String]
            getClassInheritors(definition, definition.resolveScope).collectFirst {
              case o: ScObject if o.isStatic && processedObjects.add(o.qualifiedName) => o
            }.flatMap {
              _.`type`().toOption
            }.foreach {
              processor.processType(_, place)
            }
          case _ =>
        }
    }

    processor.candidates.toSeq
  }

  private def completeImplicits(element: PsiNamedElement,
                                elementObject: Option[ScObject],
                                resultType: ScType)
                               (implicit place: ScReferenceExpression,
                                elements: Set[PsiNamedElement],
                                originalFile: PsiFile): Iterable[ScalaLookupItem] = {
    val processor = new CompletionProcessor(StdKinds.methodRef, place)
    processor.processType(resultType, place)

    processor.candidates.map { candidate =>
      val lookup = candidate.getLookupElement(
        isClassName = true,
        shouldImport = shouldImport(candidate.getElement)
      ).head

      lookup.usedImportStaticQuickfix = true
      lookup.elementToImport = Some(element)
      lookup.objectOfElementToImport = elementObject

      lookup
    }
  }

  private[this] def complete(accessAll: Boolean)
                            (nameMatches: String => Boolean)
                            (implicit place: ScReferenceExpression,
                             elements: Set[PsiNamedElement],
                             originalFile: PsiFile): Iterable[ScalaLookupItem] = {
    import place.elementScope

    val isAccessible = ResolveUtils.isAccessible(_: PsiMember, place, forCompletion = true)

    def areAccessible(member: PsiMember, containingClass: PsiClass): Boolean = accessAll ||
      (isAccessible(member) && isAccessible(containingClass))

    def inheritedIn(member: PsiMember, element: PsiNamedElement): Seq[PsiClass] =
      member.containingClass match {
        case null => Seq.empty
        case clazz =>
          import PsiClass.EMPTY_ARRAY
          val inheritors = member match {
            case _: ScValueOrVariable | _: ScFunction =>
              ClassInheritorsSearch.search(clazz, elementScope.scope, true).toArray(EMPTY_ARRAY)
            case _ => EMPTY_ARRAY
          }

          (Seq(clazz) ++ inheritors).filter { containingClass =>
            isStatic(element, containingClass) && areAccessible(member, containingClass)
          }
      }

    def createLookups(namedElement: PsiNamedElement,
                      containingClass: PsiClass,
                      overloaded: Boolean = false)
                     (shouldImport: Boolean = this.shouldImport(namedElement)) =
      new ScalaResolveResult(namedElement).getLookupElement(
        isClassName = true,
        isOverloadedForClassName = overloaded,
        shouldImport = shouldImport,
        containingClass = Some(containingClass)
      ).head

    import ScalaShortNamesCacheManager._

    val methodsLookups = allMethods(nameMatches).flatMap { method =>
      val processedClasses = mutable.HashSet.empty[PsiClass]
      inheritedIn(method, method)
        .filter(processedClasses.add)
        .flatMap { containingClass =>
          val methodName = method.name

          val overloads = containingClass match {
            case o: ScObject => o.functionsByName(methodName)
            case _ => containingClass.getAllMethods.toSeq
              .filter(_.name == methodName)
          }

          val overload = overloads match {
            case Seq() => None
            case Seq(_) => Some(method, false)
            case Seq(first, second, _*) =>
              Some(if (first.isParameterless) second else first, true)
          }

          overload.map {
            case (m, overloaded) =>
              createLookups(m, containingClass, overloaded = overloaded)(shouldImport(method))
          }
        }
    }

    val fieldsLookups = for {
      field <- allFields(nameMatches)
      if isStatic(field)

      containingClass = field.containingClass
      if containingClass != null && areAccessible(field, containingClass)
    } yield createLookups(field, containingClass)()

    val propertiesLookups = for {
      property <- allProperties(nameMatches)
      namedElement = property.declaredElements.head

      containingClass <- inheritedIn(property, namedElement)
    } yield createLookups(namedElement, containingClass)()

    methodsLookups ++ fieldsLookups ++ propertiesLookups
  }

  private[this] def allMethods(predicate: String => Boolean)
                              (implicit elementScope: ElementScope) = {
    val namesCache = ScalaShortNamesCacheManager.getInstance(elementScope.project)

    (namesCache.getAllMethodNames ++ namesCache.getAllJavaMethodNames)
      .filter(predicate)
      .flatMap(namesCache.getMethodsByName(_, elementScope.scope))
  }

  private[this] def adaptResolveResult(element: PsiNamedElement, substitutor: ScSubstitutor) =
    contextContainingClass(element).collect {
      case c: ScClass => c
      case t: ScTrait => t
    }.map(ScThisType)
      .map(substitutor.subst)
      .flatMap(_.extractClass)
      .collect {
        case o: ScObject => o
      }

  private[this] def shouldImport(element: PsiNamedElement)
                                (implicit place: ScReferenceExpression,
                                 elements: Set[PsiNamedElement],
                                 originalFile: PsiFile): Boolean = {
    val contains = if (element.getContainingFile == originalFile) {
      //complex logic to detect static methods in same file, which we shouldn't import
      val filtered = elements
        .filter(_.getContainingFile == place.getContainingFile)
        .filter(_.name == element.name)
      val objectNames = filtered.flatMap(contextContainingClassName)

      contextContainingClassName(element).exists(objectNames.contains)
    } else {
      elements.contains(element)
    }

    !contains
  }

  private[this] def contextContainingClass(element: PsiNamedElement): Option[PsiClass] =
    Option(element.nameContext).collect {
      case member: PsiMember => member
    }.flatMap { member =>
      Option(member.containingClass)
    }

  private[this] def contextContainingClassName(element: PsiNamedElement): Option[String] =
    contextContainingClass(element).flatMap { clazz =>
      Option(clazz.qualifiedName)
    }
}