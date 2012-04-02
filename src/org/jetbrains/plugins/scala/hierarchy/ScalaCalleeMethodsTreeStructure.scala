package org.jetbrains.plugins.scala.hierarchy

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.util.ArrayUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.ide.hierarchy.call.CallHierarchyNodeDescriptor
import collection.mutable.{HashMap, ArrayBuffer}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.extensions.toPsiMemberExt

/**
 * @author Alexander Podkhalyuzin
 */
class ScalaCalleeMethodsTreeStructure(project: Project, method: PsiMethod, myScopeType: String)
  extends HierarchyTreeStructure(project, new CallHierarchyNodeDescriptor(project, null, method, true, false)) {

  protected final def buildChildren(descriptor: HierarchyNodeDescriptor): Array[AnyRef] = {
    val enclosingElement: PsiMember = (descriptor.asInstanceOf[CallHierarchyNodeDescriptor]).getEnclosingElement
    val method: PsiMethod = enclosingElement match {
      case method: PsiMethod => method
      case _ => return ArrayUtil.EMPTY_OBJECT_ARRAY
    }
    val methods: ArrayBuffer[PsiMethod] = new ArrayBuffer[PsiMethod]
    method match {
      case fun: ScFunctionDefinition =>
        fun.body match {
          case Some(body) =>
            ScalaCalleeMethodsTreeStructure.visitor(body, methods)
          case _ =>
        }
      case fun: ScFunction =>
      case _ =>
        val body = method.getBody
        ScalaCalleeMethodsTreeStructure.visitor(body, methods)
    }
    val baseMethod: PsiMethod = (getBaseDescriptor.asInstanceOf[CallHierarchyNodeDescriptor]).getTargetElement.asInstanceOf[PsiMethod]
    val baseClass: PsiClass = baseMethod.containingClass
    val methodToDescriptorMap: HashMap[PsiMethod, CallHierarchyNodeDescriptor] =
      new HashMap[PsiMethod, CallHierarchyNodeDescriptor]
    val result: ArrayBuffer[CallHierarchyNodeDescriptor] = new ArrayBuffer[CallHierarchyNodeDescriptor]
    for (calledMethod <- methods if isInScope(baseClass, calledMethod, myScopeType)) {
      methodToDescriptorMap.get(calledMethod) match {
        case Some(d) => d.incrementUsageCount
        case _ =>
          val d = new CallHierarchyNodeDescriptor(myProject, descriptor, calledMethod, false, false)
          methodToDescriptorMap.put(calledMethod, d)
          result += d
      }
    }
    val overridingMethods: Array[PsiMethod] =
      OverridingMethodsSearch.search(method, method.getUseScope, true).toArray(PsiMethod.EMPTY_ARRAY)
    for (overridingMethod <- overridingMethods if isInScope(baseClass, overridingMethod, myScopeType)) {
      val node: CallHierarchyNodeDescriptor = new CallHierarchyNodeDescriptor(myProject, descriptor, overridingMethod, false, false)
      if (!result.contains(node)) result += node
    }
    return result.toArray
  }


}

object ScalaCalleeMethodsTreeStructure {
  private[hierarchy] def visitor(element: PsiElement, methods: ArrayBuffer[PsiMethod]): Unit = {
    if (element == null) return
    element match {
      case ref: ScReferenceElement =>
        val resolve = ref.resolve
        resolve match {
          case meth: PsiMethod => methods += meth
          case _ =>
        }
      case callExpression: PsiMethodCallExpression =>
        val methodExpression: PsiReferenceExpression = callExpression.getMethodExpression
        val method: PsiMethod = methodExpression.resolve.asInstanceOf[PsiMethod]
        if (method != null) {
          methods += method
        }
      case newExpression: PsiNewExpression =>
        val method: PsiMethod = newExpression.resolveConstructor
        if (method != null) {
          methods += method
        }
      case _ =>
    }
    for (child <- element.getChildren) {
      visitor(child, methods)
    }
  }
}

