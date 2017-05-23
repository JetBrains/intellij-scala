package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.JavaCompletionFeatures.GLOBAL_MEMBER_NAME
import com.intellij.codeInsight.completion._
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions.ACTION_SHOW_INTENTION_ACTIONS
import com.intellij.openapi.keymap.KeymapUtil.getFirstKeyboardShortcutText
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiClass.EMPTY_ARRAY
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
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{fileContext, nameContext}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.implicits.{CollectImplicitsProcessor, ScImplicitlyConvertible}
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScThisType
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils.isAccessible
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, StdKinds}

import scala.collection.{JavaConverters, mutable}

/**
  * @author Alexander Podkhalyuzin
  */
class ScalaGlobalMembersCompletionContributor extends ScalaCompletionContributor {

  import ScalaGlobalMembersCompletionContributor._

  extend(CompletionType.BASIC, psiElement, new CompletionProvider[CompletionParameters]() {
    def addCompletions(parameters: CompletionParameters,
                       context: ProcessingContext,
                       resultSet: CompletionResultSet): Unit = {
      val invocationCount = parameters.getInvocationCount
      if (invocationCount < 2) return

      val position = positionFromParameters(parameters)
      if (!fileContext(position).isInstanceOf[ScalaFile]) return

      val maybeReference = Option(position.getContext).collect {
        case reference: ScReferenceExpression => reference
      }

      val lookups = maybeReference.toIterable.flatMap {
        createLookups(_, accessAll = invocationCount >= 3)(resultSet.getPrefixMatcher, parameters.getOriginalFile)
      }

      lookups.find(!_.shouldImport)
        .flatMap(_ => hintString)
        .foreach(resultSet.addLookupAdvertisement)

      import JavaConverters._
      resultSet.addAllElements(lookups.asJava)
    }
  })
}

object ScalaGlobalMembersCompletionContributor {

  private def createLookups(reference: ScReferenceExpression,
                            accessAll: Boolean)
                           (implicit prefixMatcher: PrefixMatcher,
                            originalFile: PsiFile): Iterable[ScalaLookupItem] = {
    implicit def elementsSet =
      reference.getVariants(implicits = false, filterNotNamedVariants = false).collect {
        case ScalaLookupItem(element: PsiNamedElement) => element
        case element: PsiNamedElement => element
      }.toSet

    findQualifier(reference) match {
      case Some(qualifier) =>
        val maybeType = qualifier.getTypeWithoutImplicits().toOption
        maybeType.toIterable.flatMap(completeImplicits(reference, _))
      case _ if prefixMatcher.getPrefix == "" => Seq.empty
      case _ =>
        complete(reference) {
          case _ if accessAll => true
          case member => isAccessible(member, reference, forCompletion = true)
        }
    }
  }

  private def hintString: Option[String] =
    Option(CompletionService.getCompletionService.getAdvertisementText)
      .zip(Option(ActionManager.getInstance.getAction(ACTION_SHOW_INTENTION_ACTIONS)))
      .headOption.flatMap {
      case (_, action) => Option(getFirstKeyboardShortcutText(action))
    }.map(shortcut => s"To import a method statically, press $shortcut")


  private[this] def findQualifier(reference: ScReferenceExpression): Option[ScExpression] =
    reference.qualifier.orElse {
      Option(reference.getContext).collect {
        case expr: ScSugarCallExpr if expr.operation == reference => expr.getBaseExpr
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

  import JavaConverters._

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
      "scala.collection.convert.WrapAsJava",
      "scala.concurrent.duration.DurationConversions",
      "akka.pattern.AskSupport",
      "akka.pattern.PipeToSupport"
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

  private[this] def completeImplicits(ref: ScReferenceExpression, originalType: ScType)
                                     (implicit elements: Set[PsiNamedElement],
                                      originalFile: PsiFile): Iterable[ScalaLookupItem] =
    implicitProcessor(ref).candidates.toSeq.flatMap {
      ScImplicitlyConvertible.forMap(ref, _, originalType)
    }.flatMap { result =>
      val processor = new CompletionProcessor(StdKinds.methodRef, ref)
      processor.processType(result.resultType, ref)

      processor.candidates.map { candidate =>
        getLookupElement(
          candidate,
          isClassName = true,
          shouldImport = shouldImport(candidate.getElement, ref)).head
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
    }

  private[this] def complete(ref: ScReferenceExpression)
                            (isAccessibleFromReference: PsiMember => Boolean)
                            (implicit elements: Set[PsiNamedElement],
                             matcher: PrefixMatcher,
                             originalFile: PsiFile): Iterable[ScalaLookupItem] = {
    triggerFeature()

    val scope: GlobalSearchScope = ref.getResolveScope

    val namesCache = ScalaShortNamesCacheManager.getInstance(ref.getProject)

    def isAccessible(member: PsiMember, containingClass: PsiClass): Boolean =
      isAccessibleFromReference(member) && isAccessibleFromReference(containingClass)

    def inheritedIn(member: PsiMember, element: PsiNamedElement): Seq[PsiClass] =
      member.containingClass match {
        case null => Seq.empty
        case clazz =>
          val inheritors = member match {
            case _: ScValueOrVariable |
                 _: ScFunction => ClassInheritorsSearch.search(clazz, scope, true).toArray(EMPTY_ARRAY)
            case _ => EMPTY_ARRAY
          }

          (Seq(clazz) ++ inheritors).filter { containingClass =>
            isStatic(element, containingClass) && isAccessible(member, containingClass)
          }
      }


    val methodsLookups = (namesCache.getAllMethodNames ++ namesCache.getAllJavaMethodNames)
      .filter(matcher.prefixMatches)
      .flatMap { methodName =>
        val classes = new THashSet[PsiClass]

        namesCache.getMethodsByName(methodName, scope).flatMap { method =>
          inheritedIn(method, method).filter(classes.add)
            .flatMap { containingClass =>
              val overloads = containingClass match {
                case o: ScObject => o.functionsByName(methodName)
                case _ => containingClass.getAllMethods.toSeq
                  .filter(_.name == methodName)
              }

              val overload = overloads match {
                case Seq() => None
                case Seq(_) => Some(method, false)
                case Seq(first, second, _*) =>
                  Some(if (first.getParameterList.getParametersCount != 0) first else second, true)
              }

              overload.map {
                case (m, overloaded) =>
                  createLookupElement(m, containingClass, shouldImport(method, ref), overloaded = overloaded)
              }
            }
        }
      }

    def createLookups(namedElement: PsiNamedElement, containingClasses: Seq[PsiClass]): Seq[ScalaLookupItem] =
      containingClasses.map {
        createLookupElement(namedElement, _, shouldImport(namedElement, ref))
      }

    val fieldsLookups = namesCache.getAllFieldNames
      .filter(matcher.prefixMatches)
      .flatMap(namesCache.getFieldsByName(_, scope))
      .filter(isStatic).toSeq
      .flatMap { field =>
        val maybeClass = Option(field.containingClass).filter(isAccessible(field, _))
        createLookups(field, maybeClass.toSeq)
      }

    val membersLookups = namesCache.getAllScalaFieldNames
      .filter(matcher.prefixMatches)
      .flatMap { fieldName =>
        namesCache.getScalaFieldsByName(fieldName, scope).collect {
          case v: ScValueOrVariable => v
        }.flatMap { field =>
          field.declaredElements.find(_.name == fieldName)
            .map((field, _))
        }
      }.flatMap {
      case (field, namedElement) =>
        val classes = inheritedIn(field, namedElement)
        createLookups(namedElement, classes)
    }

    methodsLookups ++ fieldsLookups ++ membersLookups
  }

  private[this] def triggerFeature(): Unit =
    FeatureUsageTracker.getInstance.triggerFeatureUsed(GLOBAL_MEMBER_NAME)

  private[this] def adaptResolveResult(resolveResult: ScalaResolveResult) = {
    val ScalaResolveResult(element, substitutor) = resolveResult

    val elementObject = contextContainingClass(element).collect {
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

  private[this] def shouldImport(element: PsiNamedElement, reference: ScReferenceExpression)
                                (implicit elements: Set[PsiNamedElement], originalFile: PsiFile): Boolean = {
    val contains = if (element.getContainingFile == originalFile) {
      //complex logic to detect static methods in same file, which we shouldn't import
      val filtered = elements
        .filter(_.getContainingFile == reference.getContainingFile)
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

  private[this] def createLookupElement(member: PsiNamedElement, clazz: PsiClass, shouldImport: Boolean,
                                        overloaded: Boolean = false): ScalaLookupItem =
    getLookupElement(new ScalaResolveResult(member), isClassName = true,
      isOverloadedForClassName = overloaded, shouldImport = shouldImport, containingClass = Some(clazz)).head
}