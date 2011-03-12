package org.jetbrains.plugins.scala
package lang
package refactoring
package delete

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef
import com.intellij.psi.javadoc.PsiDocTag
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.MethodSignatureUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.safeDelete._
import com.intellij.refactoring.safeDelete.usageInfo._
import com.intellij.usageView.UsageInfo
import com.intellij.util.ArrayUtil
import com.intellij.util.Function
import com.intellij.util.IncorrectOperationException
import com.intellij.util.Processor
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.Nullable
import java.util._
import java.util.HashMap
import java.util.HashSet
import collection.JavaConversions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.impl.search.ScalaOverridengMemberSearch
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt

/**
 * This is a port of the static, private mtehods in JavaSafeDeleteProcessor.
 *
 * Much still needs to be made Scala aware.
 *
 * @see com.intellij.refactoring.safeDelete.JavaSafeDeleteProcessor
 */
object SafeDeleteProcessorUtil {
  def getUsageInsideDeletedFilter(allElementsToDelete: Array[PsiElement]): Condition[PsiElement] = {
    return new Condition[PsiElement] {
      def value(usage: PsiElement): Boolean = {
        return !(usage.isInstanceOf[PsiFile]) && isInside(usage, allElementsToDelete)
      }
    }
  }

  def findClassUsages(psiClass: PsiClass, allElementsToDelete: Array[PsiElement], usages: List[UsageInfo]): Unit = {
    val justPrivates: Boolean = containsOnlyPrivates(psiClass)
    ReferencesSearch.search(psiClass).forEach(new Processor[PsiReference] {
      def process(reference: PsiReference): Boolean = {
        val element: PsiElement = reference.getElement
        if (!isInside(element, allElementsToDelete)) {
          val parent: PsiElement = element.getParent
          if (parent.isInstanceOf[PsiReferenceList]) {
            val pparent: PsiElement = parent.getParent
            if (pparent.isInstanceOf[PsiClass]) {
              val inheritor: PsiClass = pparent.asInstanceOf[PsiClass]
              if (justPrivates) {
                if (parent.equals(inheritor.getExtendsList) || parent.equals(inheritor.getImplementsList)) {
                  usages.add(new SafeDeleteExtendsClassUsageInfo(element.asInstanceOf[PsiJavaCodeReferenceElement], psiClass, inheritor))
                  return true
                }
              }
            }
          }
          LOG.assertTrue(element.getTextRange != null)
          usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(element, psiClass, parent.isInstanceOf[PsiImportStatement]))
        }
        return true
      }
    })
  }

  def containsOnlyPrivates(aClass: PsiClass): Boolean = {
    return false // TODO
  }

  def findTypeParameterExternalUsages(typeParameter: PsiTypeParameter, usages: Collection[UsageInfo]): Unit = {
    val owner: PsiTypeParameterListOwner = typeParameter.getOwner
    if (owner != null) {
      val index: Int = owner.getTypeParameterList.getTypeParameterIndex(typeParameter)
      ReferencesSearch.search(owner).forEach(new Processor[PsiReference] {
        def process(reference: PsiReference): Boolean = {
          if (reference.isInstanceOf[PsiJavaCodeReferenceElement]) {
            val typeArgs: Array[PsiTypeElement] = (reference.asInstanceOf[PsiJavaCodeReferenceElement]).getParameterList.getTypeParameterElements
            if (typeArgs.length > index) {
              usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(typeArgs(index), typeParameter, true))
            }
          }
          return true
        }
      })
    }
  }

  @Nullable def findMethodUsages(psiMethod: PsiMethod, allElementsToDelete: Array[PsiElement], usages: List[UsageInfo]): Condition[PsiElement] = {
    val references: Collection[PsiReference] = ReferencesSearch.search(psiMethod).findAll
    if (psiMethod.isConstructor) {
      return findConstructorUsages(psiMethod, references, usages, allElementsToDelete)
    }
    val overridingElements: Array[PsiNamedElement] = ScalaOverridengMemberSearch.search(psiMethod, psiMethod.getUseScope, true)
    val overridingMethods: Array[PsiNamedElement] = overridingElements.filterNot(x => allElementsToDelete.contains(x))
    for (reference <- references) {
      val element: PsiElement = reference.getElement
      if (!isInside(element, allElementsToDelete) && !isInside(element, overridingMethods.map(x => x: PsiElement))) {
        val isReferenceInImport = PsiTreeUtil.getParentOfType(element, classOf[ScImportStmt]) != null
        usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(element, psiMethod, isReferenceInImport))
      }
    }
    val methodToReferences: HashMap[PsiNamedElement, Collection[PsiReference]] = new HashMap[PsiNamedElement, Collection[PsiReference]]
    for (overridingMethod <- overridingMethods) {
      val overridingReferences: Collection[PsiReference] = ReferencesSearch.search(overridingMethod).findAll
      methodToReferences.put(overridingMethod, overridingReferences)
    }
    val validOverriding: Set[PsiElement] = {
      // TODO
//      validateOverridingMethods(psiMethod, references, Arrays.asList(overridingMethods : _*), methodToReferences, usages, allElementsToDelete)
      overridingMethods.toSet[PsiElement]
    }
    for (method <- validOverriding) {
      method match {
        case `psiMethod` =>
        case x: PsiMethod => usages.add(new SafeDeleteOverridingMethodUsageInfo(x, psiMethod))
        case x: ScNamedElement =>
          val info = new SafeDeleteUsageInfo(x, psiMethod) // TODO SafeDeleteOverridingMemberUsageInfo
          usages.add(info)
      }
    }

    return new Condition[PsiElement] {
      def value(usage: PsiElement): Boolean = {
        if (usage.isInstanceOf[PsiFile]) return false
        return isInside(usage, allElementsToDelete) || isInside(usage, validOverriding)
      }
    }
  }

  def removeDeletedMethods(methods: Array[PsiMethod], allElementsToDelete: Array[PsiElement]): Array[PsiMethod] = {
    val list: ArrayList[PsiMethod] = new ArrayList[PsiMethod]
    for (method <- methods) {
      if (!allElementsToDelete.contains(method)) {
        list.add(method)
      }
    }
    return list.toArray(new Array[PsiMethod](list.size))
  }

  @Nullable def findConstructorUsages(constructor: PsiMethod, originalReferences: Collection[PsiReference], usages: List[UsageInfo], allElementsToDelete: Array[PsiElement]): Condition[PsiElement] = {
    val constructorsToRefs: HashMap[PsiMethod, Collection[PsiReference]] = new HashMap[PsiMethod, Collection[PsiReference]]
    val newConstructors: HashSet[PsiMethod] = new HashSet[PsiMethod]
    if (isTheOnlyEmptyDefaultConstructor(constructor)) return null
    newConstructors.add(constructor)
    constructorsToRefs.put(constructor, originalReferences)
    val passConstructors: HashSet[PsiMethod] = new HashSet[PsiMethod]
    do {
      passConstructors.clear
      for (method <- newConstructors) {
        val references: Collection[PsiReference] = constructorsToRefs.get(method)
        for (reference <- references) {
          val overridingConstructor: PsiMethod = getOverridingConstructorOfSuperCall(reference.getElement)
          if (overridingConstructor != null && !constructorsToRefs.containsKey(overridingConstructor)) {
            val overridingConstructorReferences: Collection[PsiReference] = ReferencesSearch.search(overridingConstructor).findAll
            constructorsToRefs.put(overridingConstructor, overridingConstructorReferences)
            passConstructors.add(overridingConstructor)
          }
        }
      }
      newConstructors.clear
      newConstructors.addAll(passConstructors)
    } while (!newConstructors.isEmpty)
    val validOverriding: Set[PsiMethod] = validateOverridingMethods(constructor, originalReferences, constructorsToRefs.keySet, constructorsToRefs, usages, allElementsToDelete)
    return new Condition[PsiElement] {
      def value(usage: PsiElement): Boolean = {
        if (usage.isInstanceOf[PsiFile]) return false
        return isInside(usage, allElementsToDelete) || isInside(usage, validOverriding)
      }
    }
  }

  def isTheOnlyEmptyDefaultConstructor(constructor: PsiMethod): Boolean = {
    if (constructor.getParameterList.getParameters.length > 0) return false
    val body: PsiCodeBlock = constructor.getBody
    if (body != null && body.getStatements.length > 0) return false
    return constructor.getContainingClass.getConstructors.length == 1
  }

  def validateOverridingMethods(originalMethod: PsiMethod, originalReferences: Collection[PsiReference],
                                overridingMethods: Collection[PsiMethod], methodToReferences: HashMap[PsiMethod, Collection[PsiReference]],
                                usages: List[UsageInfo], allElementsToDelete: Array[PsiElement]): Set[PsiMethod] = {
    val validOverriding: Set[PsiMethod] = new LinkedHashSet[PsiMethod](overridingMethods)
    val multipleInterfaceImplementations: Set[PsiMethod] = new HashSet[PsiMethod]
    var anyNewBadRefs: Boolean = false
    do {
      anyNewBadRefs = false
      for (overridingMethod <- overridingMethods) {
        if (validOverriding.contains(overridingMethod)) {
          val overridingReferences: Collection[PsiReference] = methodToReferences.get(overridingMethod)
          var anyOverridingRefs: Boolean = false
          import scala.util.control.Breaks._
          breakable {
            for (overridingReference <- overridingReferences) {
              val element: PsiElement = overridingReference.getElement
              if (!isInside(element, allElementsToDelete) && !isInside(element, validOverriding)) {
                anyOverridingRefs = true
                break
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
            for (reference <- originalReferences) {
              val element: PsiElement = reference.getElement
              if (!isInside(element, allElementsToDelete) && !isInside(element, overridingMethods)) {
                usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(element, originalMethod, false))
                validOverriding.clear
              }
            }
          }
        }
      }
    } while (anyNewBadRefs && !validOverriding.isEmpty)
    for (method <- validOverriding) {
      if (method != originalMethod) {
        usages.add(new SafeDeleteOverridingMethodUsageInfo(method, originalMethod))
      }
    }
    for (method <- overridingMethods) {
      if (!validOverriding.contains(method) && !multipleInterfaceImplementations.contains(method)) {
        val methodCanBePrivate: Boolean = canBePrivate(method, methodToReferences.get(method), validOverriding, allElementsToDelete)
        if (methodCanBePrivate) {
          usages.add(new SafeDeletePrivatizeMethod(method, originalMethod))
        }
      }
    }
    return validOverriding
  }

  def isMultipleInterfacesImplementation(method: PsiMethod, originalMethod: PsiMethod, allElementsToDelete: Array[PsiElement]): Boolean = {
    val methods: Array[PsiMethod] = method.findSuperMethods
    for (superMethod <- methods) {
      if (ArrayUtil.find(allElementsToDelete, superMethod) < 0 && !MethodSignatureUtil.isSuperMethod(originalMethod, superMethod)) {
        return true
      }
    }
    return false
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
            if (parent.isInstanceOf[PsiMethod] && (parent.asInstanceOf[PsiMethod]).isConstructor) {
              return parent.asInstanceOf[PsiMethod]
            }
          }
        }
      }
    }
    return null
  }

  def canBePrivate(method: PsiMethod, references: Collection[PsiReference], deleted: Collection[_ <: PsiElement], allElementsToDelete: Array[PsiElement]): Boolean = {
    val containingClass: PsiClass = method.getContainingClass
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
      case e: IncorrectOperationException => {
        LOG.assertTrue(false)
        return false
      }
    }
    for (reference <- references) {
      val element: PsiElement = reference.getElement
      if (!isInside(element, allElementsToDelete) && !isInside(element, deleted) && !facade.getResolveHelper.isAccessible(method, privateModifierList, element, null, null)) {
        return false
      }
    }
    return true
  }

  def findFieldUsages(psiField: PsiField, usages: List[UsageInfo], allElementsToDelete: Array[PsiElement]): Condition[PsiElement] = {
    val isInsideDeleted: Condition[PsiElement] = getUsageInsideDeletedFilter(allElementsToDelete)
    ReferencesSearch.search(psiField).forEach(new Processor[PsiReference] {
      def process(reference: PsiReference): Boolean = {
        if (!isInsideDeleted.value(reference.getElement)) {
          val element: PsiElement = reference.getElement
          val parent: PsiElement = element.getParent
          if (parent.isInstanceOf[PsiAssignmentExpression] && element == (parent.asInstanceOf[PsiAssignmentExpression]).getLExpression) {
            usages.add(new SafeDeleteFieldWriteReference(parent.asInstanceOf[PsiAssignmentExpression], psiField))
          }
          else {
            val range: TextRange = reference.getRangeInElement
            usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(reference.getElement, psiField, range.getStartOffset, range.getEndOffset, false, PsiTreeUtil.getParentOfType(element, classOf[PsiImportStaticStatement]) != null))
          }
        }
        return true
      }
    })
    return isInsideDeleted
  }

  def findParameterUsages(parameter: PsiParameter, usages: List[UsageInfo]): Unit = {
    val method: PsiMethod = parameter.getDeclarationScope.asInstanceOf[PsiMethod]
    val index: Int = method.getParameterList.getParameterIndex(parameter)
    ReferencesSearch.search(method).forEach(new Processor[PsiReference] {
      def process(reference: PsiReference): Boolean = {
        val element: PsiElement = reference.getElement
        var call: PsiCall = null
        if (element.isInstanceOf[PsiCall]) {
          call = element.asInstanceOf[PsiCall]
        }
        else if (element.getParent.isInstanceOf[PsiCall]) {
          call = element.getParent.asInstanceOf[PsiCall]
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
                    {
                      usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(args(i), parameter, true))
                    }
                    ({
                      i += 1; i
                    })
                  }
                }
              }
            }
          }
        }
        else if (element.isInstanceOf[PsiDocMethodOrFieldRef]) {
          if ((element.asInstanceOf[PsiDocMethodOrFieldRef]).getSignature != null) {
            @NonNls val newText: StringBuffer = new StringBuffer
            newText.append("/** @see #").append(method.getName).append('(')
            val parameters: java.util.List[PsiParameter] = new ArrayList[PsiParameter](Arrays.asList(method.getParameterList.getParameters: _*))
            parameters.remove(parameter)
            newText.append(StringUtil.join(parameters, new Function[PsiParameter, String] {
              def fun(psiParameter: PsiParameter): String = {
                return parameter.getType.getCanonicalText
              }
            }, ","))
            newText.append(")*/")
            usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(element, parameter, true) {
              override def deleteElement: Unit = {
                val javadocMethodReference: PsiDocMethodOrFieldRef#MyReference = element.getReference.asInstanceOf[PsiDocMethodOrFieldRef#MyReference]
                if (javadocMethodReference != null) {
                  javadocMethodReference.bindToText(method.getContainingClass, newText)
                }
              }
            })
          }
        }
        return true
      }
    })
    ReferencesSearch.search(parameter).forEach(new Processor[PsiReference] {
      def process(reference: PsiReference): Boolean = {
        val element: PsiElement = reference.getElement
        val docTag: PsiDocTag = PsiTreeUtil.getParentOfType(element, classOf[PsiDocTag])
        if (docTag != null) {
          usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(docTag, parameter, true))
          return true
        }
        var isSafeDelete: Boolean = false
        if (element.getParent.getParent.isInstanceOf[PsiMethodCallExpression]) {
          val call: PsiMethodCallExpression = element.getParent.getParent.asInstanceOf[PsiMethodCallExpression]
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
        }
        usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(element, parameter, isSafeDelete))
        return true
      }
    })
  }

  def isInside(place: PsiElement, ancestors: Array[PsiElement]): Boolean = {
    return isInside(place, Arrays.asList(ancestors : _*))
  }

  def isInside(place: PsiElement, ancestors: Collection[_ <: PsiElement]): Boolean = {
    for (element <- ancestors) {
      if (isInside(place, element)) return true
    }
    return false
  }

  def isInside(place: PsiElement, ancestor: PsiElement): Boolean = {
    if (SafeDeleteProcessor.isInside(place, ancestor)) return true
    if (place.isInstanceOf[PsiComment] && ancestor.isInstanceOf[PsiClass]) {
      val aClass: PsiClass = ancestor.asInstanceOf[PsiClass]
      if (aClass.getParent.isInstanceOf[PsiJavaFile]) {
        val file: PsiJavaFile = aClass.getParent.asInstanceOf[PsiJavaFile]
        if (PsiTreeUtil.isAncestor(file, place, false)) {
          if (file.getClasses.length == 1) {
            return true
          }
        }
      }
    }
    return false
  }

  private val LOG: Logger = Logger.getInstance("#com.intellij.refactoring.safeDelete.JavaSafeDeleteProcessor")
}
