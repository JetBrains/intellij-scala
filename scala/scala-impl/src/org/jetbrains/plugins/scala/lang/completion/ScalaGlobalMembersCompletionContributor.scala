package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.{JavaCompletionFeatures, _}
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.openapi.actionSystem.{ActionManager, IdeActions}
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.ResolveState.initial
import com.intellij.psi.{PsiClass, _}
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
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible.{ImplicitMapResult, forMap}
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
              ImplicitMapResult(ScalaResolveResult(element, substitutor), resultType) <- forMap(place, candidate, originalType).toSeq

              elementObject = adaptResolveResult(element, substitutor)
              item <- completeImplicits(element, elementObject, resultType)
            } yield item
          case _ => Iterable.empty
        }
      case _ if prefixMatcher.getPrefix == "" => Iterable.empty
      case _ =>
        triggerFeature()
        complete(accessAll)
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

  private def implicitElements(implicit place: ScReferenceExpression): Iterable[ScMember] = {
    import ScalaIndexKeys._
    import place.projectContext
    IMPLICITS_KEY.elements("implicit",
      place.resolveScope,
      classOf[ScMember]
    )
  }

  private def implicitCandidates(implicit place: ScReferenceExpression): Iterable[ScalaResolveResult] = {
    val processor = new CollectImplicitsProcessor(place, true)
    val processedObjects = mutable.HashSet.empty[String]

    def processElements(elements: Seq[PsiNamedElement]): Unit = elements
      .filter(isStatic)
      .foreach(processor.execute(_, initial()))

    def processType(`type`: ScType): Unit =
      processor.processType(`type`, processor.getPlace)

    def findInheritor(definition: ScTemplateDefinition): Option[ScObject] =
      getClassInheritors(definition, definition.resolveScope).collect {
        case o: ScObject if o.isStatic => o
      }.find { o =>
        processedObjects.add(o.qualifiedName)
      }

    implicitElements.collect {
      case v: ScValue => (v, () => v.declaredElements)
      case f: ScFunction => (f, () => Seq(f))
      case c: ScClass => (c, () => c.getSyntheticImplicitMethod.toSeq)
    }.flatMap {
      case (member, elements) => Option(member.containingClass).map((_, elements))
    }.foreach {
      case (_: ScObject, elements) =>
        processElements(elements())
      case (definition, _) =>
        val maybeObject = findInheritor(definition)
        maybeObject
          .flatMap(_.`type`().toOption)
          .foreach(processType)
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
      lookup.elementToImport = Option(element)
      lookup.objectOfElementToImport = elementObject

      lookup
    }
  }

  private[this] def complete(accessAll: Boolean)
                            (implicit place: ScReferenceExpression,
                             elements: Set[PsiNamedElement],
                             matcher: PrefixMatcher,
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

    val (methods, javaFields, scalaFields) = findAllMembers

    val methodsLookups = methods.flatMap { method =>
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

    val fieldsLookups = javaFields.filter(isStatic)
      .flatMap { field =>
        Option(field.containingClass)
          .filter(areAccessible(field, _))
          .map(createLookups(field, _)())
      }

    val membersLookups = scalaFields.collect {
      case v: ScValueOrVariable => v
    }.flatMap { field =>
      val namedElement = field.declaredElements.head
      inheritedIn(field, namedElement)
        .map(createLookups(namedElement, _)())
    }

    methodsLookups ++ fieldsLookups ++ membersLookups
  }

  private[this] def findAllMembers(implicit elementScope: ElementScope,
                                   matcher: PrefixMatcher) = {
    def prefixMatches: String => Boolean = matcher.prefixMatches

    val ElementScope(project, scope) = elementScope
    val namesCache = ScalaShortNamesCacheManager.getInstance(project)

    val methods = (namesCache.getAllMethodNames ++ namesCache.getAllJavaMethodNames)
      .filter(prefixMatches)
      .flatMap(namesCache.getMethodsByName(_, scope))

    val javaFields = namesCache.getAllFieldNames.toSeq
      .filter(prefixMatches)
      .flatMap(namesCache.getFieldsByName(_, scope))

    val scalaFields = namesCache.getAllScalaFieldNames
      .filter(matcher.prefixMatches)
      .flatMap(namesCache.getPropertiesByName(_, scope))

    (methods, javaFields, scalaFields)
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