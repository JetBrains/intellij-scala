package org.jetbrains.plugins.scala
package lang
package refactoring
package delete

import java.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.{Condition, TextRange}
import com.intellij.psi._
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef
import com.intellij.psi.javadoc.PsiDocTag
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.{MethodSignatureUtil, PsiTreeUtil}
import com.intellij.refactoring.safeDelete._
import com.intellij.refactoring.safeDelete.usageInfo._
import com.intellij.usageView.UsageInfo
import com.intellij.util._
import org.jetbrains.annotations.{NonNls, Nullable}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.{Constructor, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.search.ScalaOverridingMemberSearcher

import scala.collection.JavaConverters._

/**
 * This is a port of the static, private mtehods in JavaSafeDeleteProcessor.
 *
 * Much still needs to be made Scala aware.
 *
 * @see com.intellij.refactoring.safeDelete.JavaSafeDeleteProcessor
 */
object SafeDeleteProcessorUtil {
  def getUsageInsideDeletedFilter(allElementsToDelete: Array[PsiElement]): Condition[PsiElement] = {
    (usage: PsiElement) => {
      !usage.isInstanceOf[PsiFile] && isInside(usage, allElementsToDelete)
    }
  }

  private def referenceSearch(element: PsiElement) = ReferencesSearch.search(element, element.getUseScope)
  
  def findClassUsages(psiClass: PsiClass, allElementsToDelete: Array[PsiElement], usages: util.List[UsageInfo]) {
    val justPrivates: Boolean = containsOnlyPrivates(psiClass)
    referenceSearch(psiClass).forEach(new Processor[PsiReference] {
      def process(reference: PsiReference): Boolean = {
        val element: PsiElement = reference.getElement
        if (!isInside(element, allElementsToDelete)) {
          val parent: PsiElement = element.getParent
          if (parent.isInstanceOf[PsiReferenceList]) {
            val pparent: PsiElement = parent.getParent
            pparent match {
              case inheritor: PsiClass =>
                if (justPrivates) {
                  if (parent.equals(inheritor.getExtendsList) || parent.equals(inheritor.getImplementsList)) {
                    usages.add(new SafeDeleteExtendsClassUsageInfo(element.asInstanceOf[PsiJavaCodeReferenceElement], psiClass, inheritor))
                    return true
                  }
                }
              case _ =>
            }
          }
          LOG.assertTrue(element.getTextRange != null)

          val shouldDelete = element match {
            case ref: ScStableCodeReference =>
              val results = ref.multiResolveScala(false)
              def isSyntheticObject(e: PsiElement) = e.asOptionOf[ScObject].exists(_.isSyntheticObject)
              val nonSyntheticTargets = results.map(_.getElement).filterNot(isSyntheticObject)
              nonSyntheticTargets.toSet subsetOf allElementsToDelete.toSet
            case _ => true
          }

          val usagesToAdd = if (shouldDelete) {
            val isInImport = PsiTreeUtil.getParentOfType(element, classOf[ScImportStmt]) != null
            if (isInImport) Seq(new SafeDeleteReferenceJavaDeleteUsageInfo(element, psiClass, true)) // delete without review
            else Seq(new SafeDeleteReferenceJavaDeleteUsageInfo(element, psiClass, false)) // delete with review
          } else Seq() // don't delete

          usages.addAll(usagesToAdd.asJava)
        }
        true
      }
    })
  }

  def containsOnlyPrivates(aClass: PsiClass): Boolean = {
    false // TODO
  }

  def findTypeParameterExternalUsages(typeParameter: PsiTypeParameter, usages: util.Collection[UsageInfo]) {
    val owner: PsiTypeParameterListOwner = typeParameter.getOwner
    if (owner != null) {
      val index: Int = owner.getTypeParameterList.getTypeParameterIndex(typeParameter)
      referenceSearch(owner).forEach(new Processor[PsiReference] {
        def process(reference: PsiReference): Boolean = {
          reference match {
            case referenceElement: PsiJavaCodeReferenceElement =>
              val typeArgs: Array[PsiTypeElement] = referenceElement.getParameterList.getTypeParameterElements
              if (typeArgs.length > index) {
                usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(typeArgs(index), typeParameter, true))
              }
            case _ =>
          }
          true
        }
      })
    }
  }

  @Nullable def findMethodUsages(psiMethod: PsiMethod, allElementsToDelete: Array[PsiElement], usages: util.List[UsageInfo]): Condition[PsiElement] = {
    val references: util.Collection[PsiReference] = referenceSearch(psiMethod).findAll
    if (psiMethod.isConstructor) {
      return findConstructorUsages(psiMethod, references, usages, allElementsToDelete)
    }
    val overridingElements: Array[PsiNamedElement] = ScalaOverridingMemberSearcher.search(psiMethod)
    val overridingMethods: Array[PsiNamedElement] = overridingElements.filterNot(x => allElementsToDelete.contains(x))
    references.forEach { reference =>
      val element: PsiElement = reference.getElement
      if (!isInside(element, allElementsToDelete) && !isInside(element, overridingMethods.map(x => x: PsiElement))) {
        val isReferenceInImport = PsiTreeUtil.getParentOfType(element, classOf[ScImportStmt]) != null
        usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(element, psiMethod, isReferenceInImport))
      }
    }
    val methodToReferences: util.HashMap[PsiNamedElement, util.Collection[PsiReference]] = new util.HashMap[PsiNamedElement, util.Collection[PsiReference]]
    for (overridingMethod <- overridingMethods) {
      val overridingReferences: util.Collection[PsiReference] = referenceSearch(overridingMethod).findAll
      methodToReferences.put(overridingMethod, overridingReferences)
    }
    val validOverriding: util.Set[PsiElement] = {
      // TODO
      overridingMethods.toSet[PsiElement].asJava
    }
    validOverriding.forEach {
      case `psiMethod` =>
      case x: PsiMethod => usages.add(new SafeDeleteOverridingMethodUsageInfo(x, psiMethod))
      case x: ScNamedElement =>
        val info = new SafeDeleteUsageInfo(x, psiMethod) // TODO SafeDeleteOverridingMemberUsageInfo
        usages.add(info)
    }

    new Condition[PsiElement] {
      def value(usage: PsiElement): Boolean = {
        if (usage.isInstanceOf[PsiFile]) return false
        isInside(usage, allElementsToDelete) || isInside(usage, validOverriding)
      }
    }
  }

  def removeDeletedMethods(methods: Array[PsiMethod], allElementsToDelete: Array[PsiElement]): Array[PsiMethod] = {
    val list: util.ArrayList[PsiMethod] = new util.ArrayList[PsiMethod]
    for (method <- methods) {
      if (!allElementsToDelete.contains(method)) {
        list.add(method)
      }
    }
    list.toArray(new Array[PsiMethod](list.size))
  }

  @Nullable def findConstructorUsages(constructor: PsiMethod, originalReferences: util.Collection[PsiReference], usages: util.List[UsageInfo], allElementsToDelete: Array[PsiElement]): Condition[PsiElement] = {
    val constructorsToRefs: util.HashMap[PsiMethod, util.Collection[PsiReference]] = new util.HashMap[PsiMethod, util.Collection[PsiReference]]
    val newConstructors: util.HashSet[PsiMethod] = new util.HashSet[PsiMethod]
    if (isTheOnlyEmptyDefaultConstructor(constructor)) return null
    newConstructors.add(constructor)
    constructorsToRefs.put(constructor, originalReferences)
    val passConstructors: util.HashSet[PsiMethod] = new util.HashSet[PsiMethod]
    do {
      passConstructors.clear()
      newConstructors.forEach { method =>
        val references: util.Collection[PsiReference] = constructorsToRefs.get(method)
        references.forEach { reference =>
          val overridingConstructor: PsiMethod = getOverridingConstructorOfSuperCall(reference.getElement)
          if (overridingConstructor != null && !constructorsToRefs.containsKey(overridingConstructor)) {
            val overridingConstructorReferences: util.Collection[PsiReference] = referenceSearch(overridingConstructor).findAll
            constructorsToRefs.put(overridingConstructor, overridingConstructorReferences)
            passConstructors.add(overridingConstructor)
          }
        }
      }
      newConstructors.clear()
      newConstructors.addAll(passConstructors)
    } while (!newConstructors.isEmpty)
    val validOverriding: util.Set[PsiMethod] = validateOverridingMethods(constructor, originalReferences, constructorsToRefs.keySet, constructorsToRefs, usages, allElementsToDelete)
    new Condition[PsiElement] {
      def value(usage: PsiElement): Boolean = {
        if (usage.isInstanceOf[PsiFile]) return false
        isInside(usage, allElementsToDelete) || isInside(usage, validOverriding)
      }
    }
  }

  def isTheOnlyEmptyDefaultConstructor(constructor: PsiMethod): Boolean = {
    if (constructor.parameters.nonEmpty) return false
    val body: PsiCodeBlock = constructor.getBody
    if (body != null && body.getStatements.length > 0) return false
    constructor.containingClass.getConstructors.length == 1
  }

  def validateOverridingMethods(originalMethod: PsiMethod, originalReferences: util.Collection[PsiReference],
                                overridingMethods: util.Collection[PsiMethod], methodToReferences: util.HashMap[PsiMethod, util.Collection[PsiReference]],
                                usages: util.List[UsageInfo], allElementsToDelete: Array[PsiElement]): util.Set[PsiMethod] = {
    val validOverriding: util.Set[PsiMethod] = new util.LinkedHashSet[PsiMethod](overridingMethods)
    val multipleInterfaceImplementations: util.Set[PsiMethod] = new util.HashSet[PsiMethod]
    var anyNewBadRefs: Boolean = false
    do {
      anyNewBadRefs = false
      overridingMethods.forEach { overridingMethod =>
        if (validOverriding.contains(overridingMethod)) {
          val overridingReferences: util.Collection[PsiReference] = methodToReferences.get(overridingMethod)
          var anyOverridingRefs: Boolean = false
          import scala.util.control.Breaks._
          breakable {
            overridingReferences.forEach { overridingReference =>
              val element: PsiElement = overridingReference.getElement
              if (!isInside(element, allElementsToDelete) && !isInside(element, validOverriding)) {
                anyOverridingRefs = true
                break()
              }
            }
          }
          if (!anyOverridingRefs && isMultipleInterfacesImplementation(overridingMethod, originalMethod, allElementsToDelete)) {
            anyOverridingRefs = true
            multipleInterfaceImplementations.add(overridingMethod)
          }
          if (anyOverridingRefs) {
            validOverriding.remove(overridingMethod)
            anyNewBadRefs = true
            originalReferences.forEach { reference =>
              val element: PsiElement = reference.getElement
              if (!isInside(element, allElementsToDelete) && !isInside(element, overridingMethods)) {
                usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(element, originalMethod, false))
                validOverriding.clear()
              }
            }
          }
        }
      }
    } while (anyNewBadRefs && !validOverriding.isEmpty)
    validOverriding.forEach ( method =>
      if (method != originalMethod)
        usages.add(new SafeDeleteOverridingMethodUsageInfo(method, originalMethod))
    )
    overridingMethods.forEach { method =>
      if (!validOverriding.contains(method) && !multipleInterfaceImplementations.contains(method)) {
        val methodCanBePrivate: Boolean = canBePrivate(method, methodToReferences.get(method), validOverriding, allElementsToDelete)
        if (methodCanBePrivate) {
          usages.add(new SafeDeletePrivatizeMethod(method, originalMethod))
        }
      }
    }
    validOverriding
  }

  def isMultipleInterfacesImplementation(method: PsiMethod, originalMethod: PsiMethod, allElementsToDelete: Array[PsiElement]): Boolean = {
    val methods: Array[PsiMethod] = method.findSuperMethods
    for (superMethod <- methods) {
      if (ArrayUtilRt.find(allElementsToDelete, superMethod) < 0 && !MethodSignatureUtil.isSuperMethod(originalMethod, superMethod)) {
        return true
      }
    }
    false
  }

  @Nullable def getOverridingConstructorOfSuperCall(element: PsiElement): PsiMethod = {
    if (element.isInstanceOf[PsiReferenceExpression] && "super".equals(element.getText)) {
      var parent: PsiElement = element.getParent
      if (parent.isInstanceOf[PsiMethodCallExpression]) {
        parent = parent.getParent
        if (parent.isInstanceOf[PsiExpressionStatement]) {
          parent = parent.getParent
          if (parent.isInstanceOf[PsiCodeBlock]) {
            parent = parent.getParent
            parent match {
              case Constructor(constr) =>
                return constr
              case _ =>
            }
          }
        }
      }
    }
    null
  }

  def canBePrivate(method: PsiMethod, references: util.Collection[PsiReference], deleted: util.Collection[_ <: PsiElement], allElementsToDelete: Array[PsiElement]): Boolean = {
    val containingClass: PsiClass = method.containingClass
    if (containingClass == null) {
      return false
    }
    val manager: PsiManager = method.getManager
    val facade: JavaPsiFacade = JavaPsiFacade.getInstance(manager.getProject)
    val factory: PsiElementFactory = facade.getElementFactory
    var privateModifierList: PsiModifierList = null
    try {
      val newMethod: PsiMethod = factory.createMethod("x3", PsiType.VOID)
      privateModifierList = newMethod.getModifierList
      privateModifierList.setModifierProperty(PsiModifier.PRIVATE, true)
    }
    catch {
      case _: IncorrectOperationException =>
        LOG.assertTrue(false)
        return false
    }
    references.forEach { reference =>
      val element: PsiElement = reference.getElement
      if (!isInside(element, allElementsToDelete) && !isInside(element, deleted) && !facade.getResolveHelper.isAccessible(method, privateModifierList, element, null, null)) {
        return false
      }
    }
    true
  }

  def findFieldUsages(psiField: PsiField, usages: util.List[UsageInfo], allElementsToDelete: Array[PsiElement]): Condition[PsiElement] = {
    val isInsideDeleted: Condition[PsiElement] = getUsageInsideDeletedFilter(allElementsToDelete)
    referenceSearch(psiField).forEach(new Processor[PsiReference] {
      def process(reference: PsiReference): Boolean = {
        if (!isInsideDeleted.value(reference.getElement)) {
          val element: PsiElement = reference.getElement
          val parent: PsiElement = element.getParent
          parent match {
            case assignExpr: PsiAssignmentExpression if element == assignExpr.getLExpression =>
              usages.add(new SafeDeleteFieldWriteReference(assignExpr, psiField))
            case _ =>
              val range: TextRange = reference.getRangeInElement
              usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(reference.getElement, psiField, range.getStartOffset, range.getEndOffset, false, element.parentOfType(classOf[PsiImportStaticStatement]).isDefined))
          }
        }
        true
      }
    })
    isInsideDeleted
  }

  def findParameterUsages(parameter: PsiParameter, usages: util.List[UsageInfo]) {
    val method: PsiMethod = parameter.getDeclarationScope.asInstanceOf[PsiMethod]
    val index: Int = method.getParameterList.getParameterIndex(parameter)
    referenceSearch(method).forEach(new Processor[PsiReference] {
      def process(reference: PsiReference): Boolean = {
        val element: PsiElement = reference.getElement
        var call: PsiCall = null
        element match {
          case psiCall: PsiCall =>
            call = psiCall
          case _ =>
            element.getParent match {
            case psiCall: PsiCall =>
              call = psiCall
            case _ =>
          }
        }
        if (call != null) {
          val argList: PsiExpressionList = call.getArgumentList
          if (argList != null) {
            val args: Array[PsiExpression] = argList.getExpressions
            if (index < args.length) {
              if (!parameter.isVarArgs) {
                usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(args(index), parameter, true))
              }
              else {
                {
                  var i: Int = index
                  while (i < args.length) {
                    usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(args(i), parameter, true))
                    i += 1
                  }
                }
              }
            }
          }
        }
        else element match {
          case methodOrFieldRef: PsiDocMethodOrFieldRef =>
            if (methodOrFieldRef.getSignature != null) {
              @NonNls val newText: StringBuffer = new StringBuffer
              newText.append("/** @see #").append(method.name).append('(')
              val parameters: java.util.List[PsiParameter] = new util.ArrayList[PsiParameter](util.Arrays.asList(method.getParameterList.getParameters: _*))
              parameters.remove(parameter)
              newText.append(parameters.asScala.map(_.getType.getCanonicalText).mkString(","))
              newText.append(")*/")
              usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(element, parameter, true) {
                override def deleteElement() {
                  val javadocMethodReference: PsiDocMethodOrFieldRef#MyReference = element.getReference.asInstanceOf[PsiDocMethodOrFieldRef#MyReference]
                  if (javadocMethodReference != null) {
                    javadocMethodReference.bindToText(method.containingClass, newText)
                  }
                }
              })
            }
          case _ =>
        }
        true
      }
    })
    referenceSearch(parameter).forEach(new Processor[PsiReference] {
      def process(reference: PsiReference): Boolean = {
        val element: PsiElement = reference.getElement
        val docTag: PsiDocTag = PsiTreeUtil.getParentOfType(element, classOf[PsiDocTag])
        if (docTag != null) {
          usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(docTag, parameter, true))
          return true
        }
        var isSafeDelete: Boolean = false
        element.getParent.getParent match {
          case call: PsiMethodCallExpression =>
            val methodExpression: PsiReferenceExpression = call.getMethodExpression
            if (methodExpression.getText.equals(PsiKeyword.SUPER)) {
              isSafeDelete = true
            }
            else if (methodExpression.getQualifierExpression.isInstanceOf[PsiSuperExpression]) {
              val superMethod: PsiMethod = call.resolveMethod
              if (superMethod != null && MethodSignatureUtil.isSuperMethod(superMethod, method)) {
                isSafeDelete = true
              }
            }
          case _ =>
        }
        usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(element, parameter, isSafeDelete))
        true
      }
    })
  }

  def isInside(place: PsiElement, ancestors: Array[PsiElement]): Boolean = {
    isInside(place, util.Arrays.asList(ancestors : _*))
  }

  def isInside(place: PsiElement, ancestors: util.Collection[_ <: PsiElement]): Boolean = {
    ancestors.forEach { element =>
      if (isInside(place, element)) return true
    }
    false
  }

  def isInside(place: PsiElement, ancestor: PsiElement): Boolean = {
    if (SafeDeleteProcessor.isInside(place, ancestor)) return true
    if (place.isInstanceOf[PsiComment] && ancestor.isInstanceOf[PsiClass]) {
      val aClass: PsiClass = ancestor.asInstanceOf[PsiClass]
      aClass.getParent match {
        case file: PsiJavaFile =>
          if (PsiTreeUtil.isAncestor(file, place, false)) {
            if (file.getClasses.length == 1) {
              return true
            }
          }
        case _ =>
      }
    }
    false
  }

  private val LOG: Logger = Logger.getInstance("#com.intellij.refactoring.safeDelete.JavaSafeDeleteProcessor")
}
