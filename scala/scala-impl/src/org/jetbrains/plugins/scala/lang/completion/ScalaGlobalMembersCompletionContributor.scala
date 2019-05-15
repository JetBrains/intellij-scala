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
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitConversionProcessor
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible.targetTypeAndSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult, StdKinds}

import scala.collection.JavaConverters

/**
  * @author Alexander Podkhalyuzin
  */
final class ScalaGlobalMembersCompletionContributor extends ScalaCompletionContributor {

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
          case expression: ScReferenceExpression if PsiTreeUtil.getContextOfType(expression, classOf[ScalaFile]) != null =>
            implicit val place: ScReferenceExpression = expression

            val qualifier = place.qualifier.orElse {
              Option(place.getContext).collect {
                case ScSugarCallExpr(baseExpression, `place`, _) => baseExpression
              }
            }
            val finder = qualifier match {
              case None       => staticMembersFinder(place, resultSet.getPrefixMatcher, accessAll = invocationCount >= 3)
              case Some(qual) => extensionMethodsFinder(qual)
            }

            if (finder == null) return

            val lookupItems = finder.lookupItems(parameters.getOriginalFile)
            if (CompletionService.getCompletionService.getAdvertisementText != null &&
              lookupItems.exists(!_.shouldImport)) {
              hintString.foreach(resultSet.addLookupAdvertisement)
            }

            import JavaConverters._
            resultSet.addAllElements(lookupItems.asJava)
          case _ =>
        }
      }
    }
  )
}

object ScalaGlobalMembersCompletionContributor {

  private sealed abstract class GlobalMembersFinder {

    FeatureUsageTracker.getInstance.triggerFeatureUsed(JavaCompletionFeatures.GLOBAL_MEMBER_NAME)

    final def lookupItems(originalFile: PsiFile)
                         (implicit place: ScReferenceExpression): Seq[ScalaLookupItem] =
      candidates.toSeq match {
        case Seq() => Seq.empty
        case seq =>
          val elements = place.completionVariants().toSet[ScalaLookupItem].map(_.element)
          seq.map(_.createLookupItem(originalFile, elements))
      }

    protected def candidates: Iterable[GlobalMemberResult]

    protected sealed abstract class GlobalMemberResult {

      protected val resolveResult: ScalaResolveResult
      protected val isOverloadedForClassName: Boolean
      protected val containingClass: PsiClass

      def createLookupItem(implicit originalFile: PsiFile,
                           elements: Set[PsiNamedElement]): ScalaLookupItem = {
        resolveResult.getLookupElement(
          isClassName = true,
          isOverloadedForClassName = isOverloadedForClassName,
          shouldImport = shouldImport(resolveResult.element),
          containingClass = Option(containingClass)
        ).head
      }

      private def shouldImport(element: PsiNamedElement)
                              (implicit originalFile: PsiFile,
                               elements: Set[PsiNamedElement]): Boolean = element.getContainingFile match {
        case `originalFile` =>
          def contextContainingClassName(element: PsiNamedElement): Option[String] =
            contextContainingClass(element).flatMap { clazz =>
              Option(clazz.qualifiedName)
            }

          //complex logic to detect static methods in same file, which we shouldn't import
          val name = element.name
          val objectNames = for {
            e <- elements
            if e.getContainingFile == originalFile && e.name == name
            className <- contextContainingClassName(e)
          } yield className

          contextContainingClassName(element).forall(!objectNames.contains(_))
        case _ => !elements.contains(element)
      }
    }

  }

  private final class StaticMembersFinder(place: ScReferenceExpression, matcher: PrefixMatcher, accessAll: Boolean)
    extends GlobalMembersFinder {

    private implicit val ElementScope(project, scope) = place.elementScope
    private val cacheManager = ScalaShortNamesCacheManager.getInstance
    private def nameMatches(s: String): Boolean = matcher.prefixMatches(s)

    override protected def candidates: Iterable[GlobalMemberResult] = methodsLookups ++ fieldsLookups ++ propertiesLookups

    private def methodsLookups = for {
      method <- cacheManager.allFunctions(nameMatches) ++ cacheManager.allMethods(nameMatches)
      if isAccessible(method)

      containingClass <- inheritedIn(method, method)

      methodName = method.name
      overloads = containingClass match {
        case o: ScObject => o.allFunctionsByName(methodName).toSeq
        case _ => containingClass.getAllMethods.filter(_.name == methodName).toSeq
      }

      first <- overloads.headOption
      (namedElement, isOverloadedForClassName) = overloads.lift(1).fold((first, false)) { second =>
        (if (first.isParameterless) second else first, true)
      }
    } yield StaticMemberResult(namedElement, containingClass, isOverloadedForClassName)

    private def fieldsLookups = for {
      field <- cacheManager.allFields(nameMatches)
      if isAccessible(field) && isStatic(field)

      containingClass = field.containingClass
      if containingClass != null && isAccessible(containingClass)
    } yield StaticMemberResult(field, containingClass)

    private def propertiesLookups = for {
      property <- cacheManager.allProperties(nameMatches)
      if isAccessible(property)

      namedElement = property.declaredElements.head

      containingClass <- inheritedIn(property, namedElement)
    } yield StaticMemberResult(namedElement, containingClass)

    private def isAccessible(member: PsiMember) = accessAll ||
      ResolveUtils.isAccessible(member, place, forCompletion = true)

    private def inheritedIn(member: PsiMember, element: PsiNamedElement): Set[PsiClass] = member.containingClass match {
      case null => Set.empty
      case clazz =>
        import PsiClass.EMPTY_ARRAY
        val inheritors = member match {
          case _: ScValueOrVariable |
               _: ScFunction =>
            ClassInheritorsSearch.search(clazz, scope, true).toArray(EMPTY_ARRAY)
          case _ => EMPTY_ARRAY
        }

        (Set(clazz) ++ inheritors).filter { containingClass =>
          isStatic(element, containingClass) && isAccessible(containingClass)
        }
    }

    private final case class StaticMemberResult(namedElement: PsiNamedElement,
                                                containingClass: PsiClass,
                                                isOverloadedForClassName: Boolean = false) extends GlobalMemberResult {
      override protected val resolveResult = new ScalaResolveResult(namedElement)
    }

  }

  private def staticMembersFinder(place: ScReferenceExpression, prefixMatcher: PrefixMatcher, accessAll: Boolean): StaticMembersFinder =
    if (prefixMatcher.getPrefix.nonEmpty) new StaticMembersFinder(place, prefixMatcher, accessAll)
    else null


  private final class ExtensionMethodsFinder(originalType: ScType, place: ScExpression) extends GlobalMembersFinder {

    override protected def candidates: Iterable[GlobalMemberResult] = for {
      (key, conversionData) <- ImplicitConversionCache.getOrScheduleUpdate(place.elementScope)

      if ImplicitConversionProcessor.applicable(key.function, place)

      (resultType, _)       <- targetTypeAndSubstitutor(conversionData, originalType, place).toIterable
      item                  <- completeImplicits(key.function, key.containingObject, resultType)
    } yield item

    private def completeImplicits(conversion: ScFunction, conversionContainer: ScObject, resultType: ScType): Iterable[ExtensionMethodCandidate] = {
      val processor = new CompletionProcessor(StdKinds.methodRef, place)
      processor.processType(resultType, place)

      processor.candidates.map {
        ExtensionMethodCandidate(_, conversion, conversionContainer)
      }
    }

    private final case class ExtensionMethodCandidate(resolveResult: ScalaResolveResult,
                                                      elementToImport: ScFunction,
                                                      objectOfElementToImport: ScObject) extends GlobalMemberResult {

      override protected val isOverloadedForClassName = false
      override protected val containingClass: PsiClass = null

      override def createLookupItem(implicit originalFile: PsiFile,
                                    elements: Set[PsiNamedElement]): ScalaLookupItem = {
        val lookupItem = super.createLookupItem

        lookupItem.usedImportStaticQuickfix = true
        lookupItem.elementToImport = Some(elementToImport)
        lookupItem.objectOfElementToImport = Some(objectOfElementToImport)

        lookupItem
      }
    }

  }

  private def extensionMethodsFinder(qualifier: ScExpression): ExtensionMethodsFinder = {
    val qualifierType = qualifier.getTypeWithoutImplicits().toOption

    qualifierType.map(new ExtensionMethodsFinder(_, qualifier)).orNull
  }

  private def hintString: Option[String] =
    Option(ActionManager.getInstance.getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS)).map { action =>
      "To import a method statically, press " + KeymapUtil.getFirstKeyboardShortcutText(action)
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

  private[this] def contextContainingClass(element: PsiNamedElement) =
    element.nameContext match {
      case member: PsiMember => Option(member.containingClass)
      case _ => None
    }
}