package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.openapi.actionSystem.{ActionManager, IdeActions}
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.patterns.PlatformPatterns.psiElement
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
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult, StdKinds}

import scala.collection.mutable

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

  private def completeImplicits(ref: ScReferenceExpression, result: CompletionResultSet, originalFile: PsiFile,
                                originalType: ScType) {

    FeatureUsageTracker.getInstance.triggerFeatureUsed(JavaCompletionFeatures.GLOBAL_MEMBER_NAME)
    val scope: GlobalSearchScope = ref.getResolveScope
    val file = ref.getContainingFile

    val collection = StubIndex.getElements(ScalaIndexKeys.IMPLICITS_KEY, "implicit", file.getProject, scope, classOf[ScMember])

    import scala.collection.JavaConversions._

    val proc = new CollectImplicitsProcessor(ref, true)
    val processedObjects: mutable.HashSet[String] = mutable.HashSet.empty

    def processObject(o: ScObject): Unit = {
      val qualifiedName = o.qualifiedName
      if (!processedObjects(qualifiedName)) {
        processedObjects.add(qualifiedName)
        if (o.isStatic) o.getType(TypingContext.empty).foreach(proc.processType(_, ref))
      }
    }

    val names =
      Set("scala.collection.convert.DecorateAsJava",
        "scala.collection.convert.DecorateAsScala",
        "scala.collection.convert.WrapAsScala",
        "scala.collection.convert.WrapAsJava"
      )

    def checkClass(clazz: PsiClass): Unit = {
      if (clazz == null) return
      if (!names(clazz.qualifiedName)) return
      ClassInheritorsSearch.search(clazz, false).find(_.isInstanceOf[ScObject]).collect {
        case o: ScObject => processObject(o)
      }
    }

    for (element <- collection) {
      element match {
        case v: ScValue =>
          v.containingClass match {
            case o: ScObject =>
              for (d <- v.declaredElements if isStatic(d)) {
                proc.execute(d, ResolveState.initial())
              }
            case clazz => checkClass(clazz)
          }
        case f: ScFunction =>
          f.containingClass match {
            case o: ScObject =>
              if (isStatic(f)) proc.execute(element, ResolveState.initial())
            case clazz => checkClass(clazz)
          }
        case c: ScClass =>
          c.getSyntheticImplicitMethod match {
            case Some(f: ScFunction) =>
              c.containingClass match {
                case o: ScObject =>
                  if (isStatic(c)) proc.execute(f, ResolveState.initial())
                case clazz => checkClass(clazz)
              }
            case _ =>
          }
        case _ =>
      }
    }

    val elements = elementsSet(ref)

    val lookups = proc.candidates.toSeq.flatMap {
      ScImplicitlyConvertible.forMap(ref, _, originalType)
    }.flatMap { result =>
      val processor = new CompletionProcessor(StdKinds.methodRef, ref)
      processor.processType(result.resultType, ref)

      val (element, elementObject) = adaptResolveResult(result.resolveResult)
      processor.candidates.map {
        (_, element, elementObject)
      }
    }.map {
      case (candidate, element, elementObject) =>
        val lookup = getLookupElement(candidate, isClassName = true,
          shouldImport = shouldImport(candidate.getElement, elements, file, originalFile)).head
        lookup.usedImportStaticQuickfix = true
        lookup.elementToImport = Option(element)
        lookup.objectOfElementToImport = elementObject
        lookup
    }
    result.addAllElements(lookups)
  }

  private def complete(ref: ScReferenceExpression, result: CompletionResultSet, originalFile: PsiFile, invocationCount: Int): Unit = {
    FeatureUsageTracker.getInstance.triggerFeatureUsed(JavaCompletionFeatures.GLOBAL_MEMBER_NAME)
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
        }.filter {
          isStatic(method, _)
        }.filter { containingClass =>
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
      containingClasses.filter {
        isAccessible(field, _)
      }.map { containingClass =>
        val shouldImport = ScalaGlobalMembersCompletionContributor.shouldImport(namedElement, elemsSet, file, originalFile)
        showHint(shouldImport)
        createLookupElement(namedElement, containingClass, shouldImport)
      }.foreach {
        result.addElement
      }

    namesCache.getAllFieldNames.filter {
      matcher.prefixMatches
    }.flatMap {
      namesCache.getFieldsByName(_, scope)
    }.filter {
      isStatic
    }.foreach { field =>
      createLookups(field, field, Option(field.containingClass))
    }

    namesCache.getAllScalaFieldNames.filter {
      matcher.prefixMatches
    }.foreach { fieldName =>
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

  private def adaptResolveResult(resolveResult: ScalaResolveResult) = {
    val element = resolveResult.getElement

    val elementObject = Option(element).map {
      nameContext
    }.collect {
      case member: ScMember => member
    }.flatMap { member =>
      Option(member.containingClass)
    }.collect {
      case c: ScClass => c
      case t: ScTrait => t
    }.map {
      ScThisType
    }.map {
      resolveResult.substitutor.subst
    }.flatMap {
      _.extractClass
    }.collect {
      case o: ScObject => o
    }

    (element, elementObject)
  }

  private def shouldImport(element: PsiNamedElement, elements: Set[PsiNamedElement],
                           file: PsiFile, originalFile: PsiFile): Boolean = {
    def qualifiedName(element: PsiNamedElement, file: PsiFile) =
      Option(element).filter {
        _.getContainingFile == file
      }.map {
        nameContext
      }.collect {
        case member: PsiMember => member
      }.flatMap {
        member =>
          Option(member.containingClass)
      }.flatMap {
        clazz =>
          Option(clazz.qualifiedName)
      }

    val contains = if (element.getContainingFile == originalFile) {
      //complex logic to detect static methods in same file, which we shouldn't import
      qualifiedName(element, originalFile).exists { name =>
        elements.filter {
          _.name == element.name
        }.flatMap {
          qualifiedName(_, file)
        }.contains(name)
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