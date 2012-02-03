package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.completion._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScPrefixExpr, ScPostfixExpr, ScInfixExpr, ScReferenceExpression}
import com.intellij.featureStatistics.FeatureUsageTracker
import search.{PsiShortNamesCache, GlobalSearchScope}
import gnu.trove.THashSet
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScObject}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.util.ProcessingContext
import collection.mutable.HashSet
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.actionSystem.{ActionManager, IdeActions}
import org.jetbrains.plugins.scala.caches.ScalaCachesManager
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import stubs.StubIndex
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.resolve.processor.{CompletionProcessor, CollectMethodsProcessor}
import org.jetbrains.plugins.scala.lang.resolve.{StdKinds, ScalaResolveResult, ResolveUtils}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScVariable, ScValue}
import org.jetbrains.plugins.scala.extensions.toPsiClassExt

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaGlobalMembersCompletionContributor extends CompletionContributor {  
  extend(CompletionType.CLASS_NAME, psiElement, new CompletionProvider[CompletionParameters]() {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      val position: PsiElement = parameters.getPosition
      if (!position.getContainingFile.isInstanceOf[ScalaFile]) return
      val parent: PsiElement = position.getParent
      parent match {
        case ref: ScReferenceExpression =>
          val qualifier = ref.qualifier match {
            case Some(qual) => qual
            case None =>
              ref.getParent match {
                case inf: ScInfixExpr if inf.operation == ref => inf.getBaseExpr
                case posf: ScPostfixExpr if posf.operation == ref => posf.getBaseExpr
                case pref: ScPrefixExpr if pref.operation == ref => pref.getBaseExpr
                case _ =>
                  if (result.getPrefixMatcher.getPrefix == "") return
                  complete(ref, result, parameters.getOriginalFile)
                  return
              }
          }
          val typeWithoutImplicits = qualifier.getTypeWithoutImplicits(TypingContext.empty)
          if (typeWithoutImplicits.isEmpty) return
          val tp = typeWithoutImplicits.get
          completeImplicits(ref, result, parameters.getOriginalFile, tp)
        case _ =>
      }
    }
  })

  private def isStatic(member: PsiNamedElement): Boolean = {
    ScalaPsiUtil.nameContext(member) match {
      case memb: PsiMember =>
        val containingClass = memb.getContainingClass
        if (containingClass == null) return false
        val qualifiedName = containingClass.qualifiedName + "." + member.getName
        for (excluded <- CodeInsightSettings.getInstance.EXCLUDED_PACKAGES) {
          if (qualifiedName == excluded || qualifiedName.startsWith(excluded + ".")) {
            return false
          }
        }
        containingClass match {
          case o: ScObject => true
          case _: ScTypeDefinition => false
          case _ => memb.hasModifierProperty("static")
        }
    }
  }

  private def completeImplicits(ref: ScReferenceExpression, result: CompletionResultSet, originalFile: PsiFile,
                                originalType: ScType) {
    FeatureUsageTracker.getInstance.triggerFeatureUsed(JavaCompletionFeatures.GLOBAL_MEMBER_NAME)
    val matcher: PrefixMatcher = result.getPrefixMatcher
    val scope: GlobalSearchScope = ref.getResolveScope
    val file = ref.getContainingFile

    val elemsSet = new HashSet[PsiNamedElement]
    def addElemToSet(elem: PsiNamedElement) {
      elemsSet += elem
    }

    def elemsSetContains(elem: PsiNamedElement): Boolean = {
      if (elem.getContainingFile == originalFile) {
        //complex logic to detect static methods in same file, which we shouldn't import
        val name = elem.getName
        val containingClass = ScalaPsiUtil.nameContext(elem) match {
          case member: PsiMember => member.getContainingClass
          case _ => null
        }
        if (containingClass == null) return false
        val qualName = containingClass.qualifiedName
        if (qualName == null) return false
        for {
          element <- elemsSet
          if element.getName == name
          if element.getContainingFile == file
          cClass = ScalaPsiUtil.nameContext(element) match {
            case member: PsiMember => member.getContainingClass
            case _ => null
          }
          if cClass != null
          if cClass.qualifiedName != null
          if cClass.qualifiedName == qualName
        } {
          return true
        }
        false
      } else elemsSet.contains(elem)
    }

    val collection = StubIndex.getInstance.get(ScalaIndexKeys.IMPLICITS_KEY, "implicit", file.getProject, scope)
    
    import scala.collection.JavaConversions._
    
    val proc = new ref.CollectImplicitsProcessor(true)
    for (element <- collection) {
      element match {
        case v: ScValue =>
          for (d <- v.declaredElements if isStatic(d)) {
            proc.execute(d, ResolveState.initial())
          }
        case f: ScFunction if isStatic(f) =>
          proc.execute(element, ResolveState.initial())
        case _ =>
      }
    }
    val candidates = proc.candidates.map(ref.forMap(_, originalType))

    ref.getVariants(false, false).foreach {
      case (_, elem: PsiNamedElement, _) => addElemToSet(elem)
      case elem: PsiNamedElement => addElemToSet(elem)
    }

    val iterator = candidates.iterator
    while (iterator.hasNext) {
      val next = iterator.next()
      if (next._1) {
        val retTp = next._4
        val c = new CompletionProcessor(StdKinds.methodRef)
        c.processType(retTp, ref)
        for (elem <- c.candidates) {
          val shouldImport = !elemsSetContains(elem.getElement)
          //todo: overloads?
          val lookup: LookupElement = ResolveUtils.getLookupElement(elem, isClassName = true,
            isOverloadedForClassName = false, shouldImport = shouldImport, isInStableCodeReference = false).apply(0)._1
          lookup.putUserData(ResolveUtils.usedImportStaticQuickfixKey, java.lang.Boolean.TRUE)
          lookup.putUserData(ResolveUtils.elementToImportKey, next._2.getElement)
          result.addElement(lookup)
        }
      }
    }
  }

  private def complete(ref: ScReferenceExpression, result: CompletionResultSet, originalFile: PsiFile) {
    FeatureUsageTracker.getInstance.triggerFeatureUsed(JavaCompletionFeatures.GLOBAL_MEMBER_NAME)
    val matcher: PrefixMatcher = result.getPrefixMatcher
    val scope: GlobalSearchScope = ref.getResolveScope
    val file = ref.getContainingFile

    var hintShown: Boolean = false
    def showHint(shouldImport: Boolean) {
      if (!hintShown && !shouldImport && CompletionService.getCompletionService.getAdvertisementText == null) {
        val actionId = IdeActions.ACTION_SHOW_INTENTION_ACTIONS
        val shortcut: String =
          KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance.getAction(actionId))
        if (shortcut != null) {
          CompletionService.getCompletionService.setAdvertisementText("To import a method statically, press " + shortcut)
        }
        hintShown = true
      }
    }

    val elemsSet = new HashSet[PsiNamedElement]
    def addElemToSet(elem: PsiNamedElement) {
      elemsSet += elem
    }

    def elemsSetContains(elem: PsiNamedElement): Boolean = {
      if (elem.getContainingFile == originalFile) {
        //complex logic to detect static methods in same file, which we shouldn't import
        val name = elem.getName
        val containingClass = ScalaPsiUtil.nameContext(elem) match {
          case member: PsiMember => member.getContainingClass
          case _ => null
        }
        if (containingClass == null) return false
        val qualName = containingClass.qualifiedName
        if (qualName == null) return false
        for {
          element <- elemsSet
          if element.getName == name
          if element.getContainingFile == file
          cClass = ScalaPsiUtil.nameContext(element) match {
            case member: PsiMember => member.getContainingClass
            case _ => null
          }
          if cClass != null
          if cClass.qualifiedName != null
          if cClass.qualifiedName == qualName
        } {
          return true
        }
        false
      } else elemsSet.contains(elem)
    }

    ref.getVariants(false, false).foreach {
      case (_, elem: PsiNamedElement, _) => addElemToSet(elem)
      case elem: PsiNamedElement => addElemToSet(elem)
    }

    val namesCache: PsiShortNamesCache =
      PsiShortNamesCache.getInstance(ref.getProject)

    val methodNamesIterator = namesCache.getAllMethodNames.iterator

    while (methodNamesIterator.hasNext) {
      val methodName = methodNamesIterator.next()
      if (matcher.prefixMatches(methodName)) {
        val classes = new THashSet[PsiClass]
        val methodsIterator = namesCache.getMethodsByName(methodName, scope).iterator
        while (methodsIterator.hasNext) {
          val method = methodsIterator.next()
          if (isStatic(method)) {
            val containingClass: PsiClass = method.getContainingClass
            assert(containingClass != null)
            if (classes.add(containingClass) &&
              ResolveUtils.isAccessible(containingClass, ref)) {
              val shouldImport = !elemsSetContains(method)
              showHint(shouldImport)

              val overloads = containingClass match {
                case o: ScObject => o.functionsByName(methodName)
                case _ => containingClass.getAllMethods.toSeq.filter(m => m.getName == methodName)
              }
              assert(!overloads.isEmpty)
              if (overloads.size == 1) {
                result.addElement(createLookupElement(method, containingClass, shouldImport))
              }
              else {
                val lookup = createLookupElement(if (overloads(0).getParameterList.getParametersCount == 0)
                  overloads(1) else overloads(0), containingClass, shouldImport, true)
                result.addElement(lookup)
              }
            }
          }
        }
      }
    }

    val fieldNamesIterator = namesCache.getAllFieldNames.iterator

    while (fieldNamesIterator.hasNext) {
      val fieldName = fieldNamesIterator.next()
      if (matcher.prefixMatches(fieldName)) {
        val fieldsIterator = namesCache.getFieldsByName(fieldName, scope).iterator
        while (fieldsIterator.hasNext) {
          val field = fieldsIterator.next()
          if (isStatic(field)) {
            val containingClass: PsiClass = field.getContainingClass
            assert(containingClass != null)
            if (ResolveUtils.isAccessible(field, ref)) {
              val shouldImport = !elemsSetContains(field)
              showHint(shouldImport)

              result.addElement(createLookupElement(field, containingClass, shouldImport))
            }
          }
        }
      }
    }

    val scalaNamesManager = ScalaCachesManager.getInstance(ref.getProject).getNamesCache

    val scalaFieldsIterator = scalaNamesManager.getAllScalaFieldNames.iterator

    while (scalaFieldsIterator.hasNext) {
      val fieldName = scalaFieldsIterator.next()
      if (matcher.prefixMatches(fieldName)) {
        val fieldsIterator = scalaNamesManager.getScalaFieldsByName(fieldName, scope).iterator
        while (fieldsIterator.hasNext) {
          val field = fieldsIterator.next()
          val namedElement = field match {
            case v: ScValue => v.declaredElements.find(_.getName == fieldName).getOrElse(null)
            case v: ScVariable => v.declaredElements.find(_.getName == fieldName).getOrElse(null)
          }
          if (namedElement != null && isStatic(namedElement)) {
            val containingClass: PsiClass = field.getContainingClass
            assert(containingClass != null)
            if (ResolveUtils.isAccessible(field, ref)) {
              val shouldImport = !elemsSetContains(namedElement)
              showHint(shouldImport)

              result.addElement(createLookupElement(namedElement, containingClass, shouldImport))
            }
          }
        }
      }
    }
  }

  private def createLookupElement(member: PsiNamedElement, clazz: PsiClass, shouldImport: Boolean,
                                  overloaded: Boolean = false): LookupElement = {
    ResolveUtils.getLookupElement(new ScalaResolveResult(member), isClassName = true,
      isOverloadedForClassName = overloaded, shouldImport = shouldImport, isInStableCodeReference = false).apply(0)._1
  }
}