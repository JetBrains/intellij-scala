package org.jetbrains.plugins.scala.lang.refactoring.delete

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Condition
import com.intellij.psi._
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef
import com.intellij.psi.javadoc.PsiDocTag
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.{MethodSignatureUtil, PsiTreeUtil}
import com.intellij.refactoring.safeDelete._
import com.intellij.refactoring.safeDelete.usageInfo._
import com.intellij.usageView.UsageInfo
import com.intellij.util._
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScConstructorPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScTypeArgs, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.search.ScalaOverridingMemberSearcher
import org.jetbrains.plugins.scala.lang.psi.types.api.PsiTypeConstants

import java.util
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * This is a port of the static, private methods in JavaSafeDeleteProcessor.
 *
 * Much still needs to be made Scala aware.
 *
 * @see com.intellij.refactoring.safeDelete.JavaSafeDeleteProcessor
 */
object SafeDeleteProcessorUtil {
  def getUsageInsideDeletedFilter(allElementsToDelete: Array[PsiElement]): Condition[PsiElement] = {
    (usage: PsiElement) => {
      !usage.is[PsiFile] && isInside(usage, allElementsToDelete)
    }
  }

  private def referenceSearch(element: PsiElement) = ReferencesSearch.search(element, element.getUseScope)
  
  def findClassUsages(psiClass: PsiClass, allElementsToDelete: Array[PsiElement], usages: util.List[_ >: UsageInfo]): Unit = {
    val justPrivates: Boolean = containsOnlyPrivates(psiClass)
    referenceSearch(psiClass).forEach(new Processor[PsiReference] {
      override def process(reference: PsiReference): Boolean = {
        val element: PsiElement = reference.getElement
        if (!isInside(element, allElementsToDelete)) {
          val parent: PsiElement = element.getParent
          if (parent.is[PsiReferenceList]) {
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
              nonSyntheticTargets.toSet[PsiElement] subsetOf allElementsToDelete.toSet
            case _ => true
          }

          val usagesToAdd = if (shouldDelete) {
            val isInImport = ScalaPsiUtil.getParentImportStatement(element) != null
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

  def findTypeParameterExternalUsages(typeParameter: PsiTypeParameter, usages: util.Collection[_ >: UsageInfo]): Unit = {
    val owner: PsiTypeParameterListOwner = typeParameter.getOwner
    if (owner != null) {
      val index: Int = owner.getTypeParameterList.getTypeParameterIndex(typeParameter)
      referenceSearch(owner).forEach(new Processor[PsiReference] {
        override def process(reference: PsiReference): Boolean = {
          def addTypeArgUsageInfo(typeArgs: Seq[PsiElement]): Unit =
            if (typeArgs.lengthIs > index)
              usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(typeArgs(index), typeParameter, true))

          reference match {
            case referenceElement: PsiJavaCodeReferenceElement =>
              val typeArgs: Array[PsiTypeElement] = referenceElement.getParameterList.getTypeParameterElements
              addTypeArgUsageInfo(typeArgs.toSeq)
            case ref: ScReference =>
              ref.parentOfType(Seq(classOf[ScGenericCall], classOf[ScConstructorInvocation])) match {
                case Some(ScGenericCall(referencedExpr, typeArgs)) if referencedExpr == ref || referencedExpr == ref.getParent =>
                  addTypeArgUsageInfo(typeArgs)
                case Some(ScConstructorInvocation(ScParameterizedTypeElement(typeElement, typeArgs), _)) if ref.getParent == typeElement =>
                  addTypeArgUsageInfo(typeArgs)
                case None =>
                  // Nested type arguments like `def foo: Foo[Bar[A, Baz[B], C]]`
                  ref.parentsInFile.takeWhile(_.is[ScTypeArgs, ScTypeElement]).collectFirst {
                    case ScParameterizedTypeElement(_, typeArgs) =>
                      addTypeArgUsageInfo(typeArgs)
                  }
                case _ =>
              }
            case _ =>
          }
          true
        }
      })
    }
  }

  @Nullable def findMethodUsages(psiMethod: PsiMethod, allElementsToDelete: Array[PsiElement], usages: util.List[_ >: UsageInfo]): Condition[PsiElement] = {
    val references: util.Collection[PsiReference] = referenceSearch(psiMethod).findAll
    if (psiMethod.isConstructor) {
      return findConstructorUsages(psiMethod, references, usages, allElementsToDelete)
    }
    val overridingElements: Array[PsiNamedElement] = ScalaOverridingMemberSearcher.search(psiMethod)
    val overridingMethods: Array[PsiNamedElement] = overridingElements.filterNot(x => allElementsToDelete.contains(x))
    references.forEach { reference =>
      val element: PsiElement = reference.getElement
      if (!isInside(element, allElementsToDelete) && !isInside(element, overridingMethods.map(x => x: PsiElement))) {
        val isReferenceInImport = ScalaPsiUtil.getParentImportStatement(element) != null
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
      override def value(usage: PsiElement): Boolean = {
        if (usage.is[PsiFile]) return false
        isInside(usage, allElementsToDelete) || isInside(usage, validOverriding)
      }
    }
  }

  @Nullable def findConstructorUsages(constructor: PsiMethod, originalReferences: util.Collection[PsiReference], usages: util.List[_ >: UsageInfo], allElementsToDelete: Array[PsiElement]): Condition[PsiElement] = {
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
      override def value(usage: PsiElement): Boolean = {
        if (usage.is[PsiFile]) return false
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
                                usages: util.List[_ >: UsageInfo], allElementsToDelete: Array[PsiElement]): util.Set[PsiMethod] = {
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
    if (element.is[PsiReferenceExpression] && element.textMatches("super")) {
      var parent: PsiElement = element.getParent
      if (parent.is[PsiMethodCallExpression]) {
        parent = parent.getParent
        if (parent.is[PsiExpressionStatement]) {
          parent = parent.getParent
          if (parent.is[PsiCodeBlock]) {
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
      val newMethod: PsiMethod = factory.createMethod("x3", PsiTypeConstants.Void)
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

  private def findMethodOrConstructorInvocation(element: PsiElement): Iterator[ImplicitArgumentsOwner] = {
    val invocation = element match {
      case invocation: MethodInvocation => Some(invocation)
      case Parent(invocation: MethodInvocation) => Some(invocation)
      case Parent((_: ScGenericCall) & Parent(invocation: MethodInvocation)) => Some(invocation)
      case invocation: ScSelfInvocation => Some(invocation)
      case Parent(Parent(invocation: ScConstructorInvocation)) => Some(invocation)
      case Parent(Parent((_: ScParameterizedTypeElement) & Parent(invocation: ScConstructorInvocation))) => Some(invocation)
      case _ => None
    }

    invocation match {
      case Some(call: ScMethodCall) => call.withParents.takeWhile(_.is[ScMethodCall]).map(_.asInstanceOf[ScMethodCall])
      case _ => invocation.iterator
    }
  }

  def findParameterUsages(parameter: ScParameter, usages: util.List[_ >: UsageInfo]): Unit = {
    val owner = parameter.owner
    val namedArguments = mutable.Set.empty[PsiElement]
    def searchMethodOrConstructorUsages(methodLike: PsiElement, parameter: ScParameter): Unit =
      referenceSearch(methodLike).forEach(new Processor[PsiReference] {
        override def process(reference: PsiReference): Boolean = {
          val element: PsiElement = reference.getElement
          for {
            call <- findMethodOrConstructorInvocation(element)
            (arg, param) <- call.matchedParameters
            if param.psiParam.contains(parameter)
          } {
            // named arguments should be deleted in whole
            val realArg = arg.getParent match {
              case namedArg: ScAssignment if namedArg != call =>
                namedArguments += namedArg
                namedArg
              case _ => arg
            }
            usages.add(new SafeDeleteScalaArgumentDeleteUsageInfo(realArg, parameter, true))
          }

          element.getParent match {
            case ScConstructorPattern(_, args) =>
              args.patterns.lift(parameter.index).foreach { arg =>
                usages.add(new SafeDeleteScalaArgumentDeleteUsageInfo(arg, parameter, false))
              }
            case _ =>
          }

          element match {
            case methodOrFieldRef: PsiDocMethodOrFieldRef if methodOrFieldRef.getSignature != null =>
              owner match {
                case method: PsiMethod =>
                  val newText: StringBuffer = new StringBuffer
                  newText.append("/** @see #").append(method.name).append('(')
                  val parameters: java.util.List[PsiParameter] = new util.ArrayList[PsiParameter](util.Arrays.asList(method.getParameterList.getParameters: _*))
                  parameters.remove(parameter)
                  newText.append(parameters.asScala.map(_.getType.getCanonicalText).mkString(","))
                  newText.append(")*/")
                  usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(element, parameter, true) {
                    override def deleteElement(): Unit = {
                      val javadocMethodReference = element.getReference.asInstanceOf[PsiDocMethodOrFieldRef#MyReference]
                      if (javadocMethodReference != null) {
                        javadocMethodReference.bindToText(newText)
                      }
                    }
                  })
              }
            case _ =>
          }
          true
        }
      })
    searchMethodOrConstructorUsages(owner, parameter)
    owner match {
      case ScPrimaryConstructor.ofClass(clazz) if clazz.isCase =>
        def searchInDesugaredMethod(f: ScFunction): Unit =
          searchMethodOrConstructorUsages(f, f.parameters(parameter.index))

        // copy method
        clazz.syntheticMethods
          .find(_.name == ScFunction.CommonNames.Copy)
          .foreach(searchInDesugaredMethod)

        // apply
        clazz.fakeCompanionModule
          .flatMap(_.syntheticMethods.find(_.name == ScFunction.CommonNames.Apply))
          .foreach(searchInDesugaredMethod)

        // unapply
        clazz.fakeCompanionModule
          .flatMap(_.syntheticMethods.find(_.name == ScFunction.CommonNames.Unapply))
          .foreach(searchMethodOrConstructorUsages(_, parameter))
      case _ =>
    }

    referenceSearch(parameter).forEach(new Processor[PsiReference] {
      override def process(reference: PsiReference): Boolean = {
        val element: PsiElement = reference.getElement
        val docTag: PsiDocTag = PsiTreeUtil.getParentOfType(element, classOf[PsiDocTag])
        if (docTag != null) {
          usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(docTag, parameter, true))
          return true
        }
        val isSafeDelete: Boolean =
          element.getParent.getParent match {
            case call: PsiMethodCallExpression =>
              val methodExpression: PsiReferenceExpression = call.getMethodExpression
              if (methodExpression.textMatches(PsiKeyword.SUPER)) {
                true
              } else if (methodExpression.getQualifierExpression.is[PsiSuperExpression]) {
                owner match {
                  case method: PsiMethod =>
                    val superMethod: PsiMethod = call.resolveMethod
                    superMethod != null && MethodSignatureUtil.isSuperMethod(superMethod, method)
                  case _ =>
                    false
                }
              } else {
                false
              }
            case _ => false
          }
        val isNamedArgument = namedArguments.contains(element.getParent)
        // named arguments are handled above
        if (!isNamedArgument) {
          usages.add(new SafeDeleteScalaArgumentDeleteUsageInfo(element, parameter, isSafeDelete))
        }
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
    if (place.is[PsiComment] && ancestor.is[PsiClass]) {
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
