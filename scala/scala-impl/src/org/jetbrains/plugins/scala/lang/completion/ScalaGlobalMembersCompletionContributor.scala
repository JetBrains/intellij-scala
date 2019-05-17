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
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{isImplicit, isStatic}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitConversionProcessor
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible.targetTypeAndSubstitutor
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaStubsUtil.classesToImportFor
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
          case refExpr: ScReferenceExpression if PsiTreeUtil.getContextOfType(refExpr, classOf[ScalaFile]) != null =>

            val qualifier = refExpr.qualifier.orElse(desugaredQualifier(refExpr))

            val finder = qualifier match {
              case None       => staticMembersFinder(refExpr, resultSet.getPrefixMatcher, accessAll = invocationCount >= 3)
              case Some(qual) => extensionMethodsFinder(qual)
            }

            if (finder == null) return

            val lookupItems = finder.lookupItems(parameters.getOriginalFile, refExpr)
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

    final def lookupItems(originalFile: PsiFile, reference: ScReferenceExpression): Seq[ScalaLookupItem] =
      candidates.filterNot(c => isInExcludedPackage(c.classToImport)).toSeq match {
        case Seq() => Seq.empty
        case globalCandidates =>
          val simpleElements = reference.completionVariants().toSet[ScalaLookupItem].map(_.element)
          globalCandidates.flatMap(_.createLookupItem(originalFile, simpleElements))
      }

    protected def candidates: Iterable[GlobalMemberResult]

    protected sealed abstract class GlobalMemberResult {

      val classToImport: PsiClass

      protected val resolveResult: ScalaResolveResult
      protected val isOverloadedForClassName: Boolean
      protected val containingClass: PsiClass
      protected val elementToImport: PsiNamedElement

      def createLookupItem(originalFile: PsiFile,
                           elements: Set[PsiNamedElement]): Option[ScalaLookupItem] = {
        resolveResult.getLookupElement(
          isClassName = true,
          isOverloadedForClassName = isOverloadedForClassName,
          shouldImport = shouldImport(resolveResult.element, originalFile, elements),
          containingClass = Option(containingClass)
        ).headOption.map { lookupItem =>
          lookupItem.classToImport = Some(classToImport)
          lookupItem.elementToImport = Some(elementToImport)
          lookupItem
        }
      }

      private def shouldImport(element: PsiNamedElement,
                               originalFile: PsiFile,
                               elements: Set[PsiNamedElement]): Boolean = element.getContainingFile match {
        case `originalFile` =>
          def contextContainingClassName(element: PsiNamedElement): Option[String] =
            element.containingClassOfNameContext.flatMap(_.qualifiedName.toOption)

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

      classToImport <- classesToImportFor(method)

      // filter out type class instances, such as scala.math.Numeric.String, to avoid too many results.
      if !isImplicit(classToImport)

      methodName = method.name
      overloads = classToImport match {
        case o: ScObject => o.allFunctionsByName(methodName).toSeq
        case _ => classToImport.getAllMethods.filter(_.name == methodName).toSeq
      }

      first <- overloads.headOption
      (namedElement, isOverloadedForClassName) = overloads.lift(1).fold((first, false)) { second =>
        (if (first.isParameterless) second else first, true)
      }
    } yield StaticMemberResult(namedElement, classToImport, isOverloadedForClassName)

    private def fieldsLookups = for {
      field <- cacheManager.allFields(nameMatches)
      if isAccessible(field) && isStatic(field)

      classToImport = field.containingClass
      if classToImport != null && isAccessible(classToImport)
    } yield StaticMemberResult(field, classToImport)

    private def propertiesLookups = for {
      property <- cacheManager.allProperties(nameMatches)
      if isAccessible(property)

      namedElement = property.declaredElements.head

      classToImport <- classesToImportFor(property)
    } yield StaticMemberResult(namedElement, classToImport)

    private def isAccessible(member: PsiMember) = accessAll ||
      ResolveUtils.isAccessible(member, place, forCompletion = true)

    private final case class StaticMemberResult(elementToImport: PsiNamedElement,
                                                containingClass: PsiClass,
                                                isOverloadedForClassName: Boolean = false) extends GlobalMemberResult {
      val classToImport: PsiClass = containingClass

      override protected val resolveResult = new ScalaResolveResult(elementToImport)
    }

  }

  private def staticMembersFinder(place: ScReferenceExpression, prefixMatcher: PrefixMatcher, accessAll: Boolean): StaticMembersFinder =
    if (prefixMatcher.getPrefix.nonEmpty) new StaticMembersFinder(place, prefixMatcher, accessAll)
    else null


  private final class ExtensionMethodsFinder(originalType: ScType, place: ScExpression) extends GlobalMembersFinder {
    lazy val originalTypeMemberNames: collection.Set[String] = candidatesForType(originalType).map(_.name)

    override protected def candidates: Iterable[GlobalMemberResult] = for {
      (key, conversionData) <- ImplicitConversionCache.getOrScheduleUpdate(place.elementScope)

      if ImplicitConversionProcessor.applicable(key.function, place)

      (resultType, _)       <- targetTypeAndSubstitutor(conversionData, originalType, place).toIterable
      item                  <- extensionCandidates(key.function, key.containingObject, resultType)
    } yield item

    private def extensionCandidates(conversion: ScFunction, conversionContainer: ScObject, resultType: ScType): Iterable[ExtensionMethodCandidate] = {
      val newCandidates =
        candidatesForType(resultType)
          .filterNot(c => originalTypeMemberNames.contains(c.name))

      newCandidates.map {
        ExtensionMethodCandidate(_, conversion, conversionContainer)
      }
    }

    private def candidatesForType(tp: ScType): collection.Set[ScalaResolveResult] = {
      val processor = new CompletionProcessor(StdKinds.methodRef, place)
      processor.processType(tp, place)
      processor.candidatesS
    }

    private final case class ExtensionMethodCandidate(resolveResult: ScalaResolveResult,
                                                      elementToImport: ScFunction,
                                                      classToImport: ScObject) extends GlobalMemberResult {

      override protected val isOverloadedForClassName = false
      override protected val containingClass: PsiClass = null

      override def createLookupItem(originalFile: PsiFile,
                                    elements: Set[PsiNamedElement]): Option[ScalaLookupItem] = {
        super.createLookupItem(originalFile, elements).map { lookupItem =>
          lookupItem.usedImportStaticQuickfix = true
          lookupItem
        }
      }
    }
  }

  private def isInExcludedPackage(c: PsiClass): Boolean = {
    val qName = c.qualifiedName
    if (qName == null) false
    else {
      CodeInsightSettings.getInstance.EXCLUDED_PACKAGES.exists { excludedPackage =>
        qName == excludedPackage ||  qName.startsWith(excludedPackage + ".")
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

  private def stringContextQualifier(lit: ScInterpolatedStringLiteral): Option[ScExpression] =
    lit.getStringContextExpression.flatMap {
      case ScMethodCall(ref: ScReferenceExpression, _) => ref.qualifier
      case _                                           => None
    }

  private def desugaredQualifier(refExpr: ScReferenceExpression): Option[ScExpression] =
    refExpr.getContext match {
      case ScSugarCallExpr(baseExpression, `refExpr`, _) => Option(baseExpression)
      case lit: ScInterpolatedStringLiteral              => stringContextQualifier(lit)
      case _                                             => None
    }
}