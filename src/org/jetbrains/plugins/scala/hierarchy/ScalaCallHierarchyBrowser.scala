package org.jetbrains.plugins.scala.hierarchy

import java.util.{Comparator, Map}
import javax.swing.{JComponent, JTree}

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase._
import com.intellij.ide.hierarchy.call.CallHierarchyNodeDescriptor
import com.intellij.ide.hierarchy.{CallHierarchyBrowserBase, HierarchyNodeDescriptor, HierarchyTreeStructure, JavaHierarchyUtil}
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiMethod}
import com.intellij.ui.PopupHandler

/**
 * @author Alexander Podkhalyuzin
 */
final class ScalaCallHierarchyBrowser(project: Project, method: PsiMethod)
  extends CallHierarchyBrowserBase(project, method) {
  protected def createTrees(type2TreeMap: Map[String, JTree]): Unit = {
    var group: ActionGroup = ActionManager.getInstance.getAction(IdeActions.GROUP_CALL_HIERARCHY_POPUP).asInstanceOf[ActionGroup]
    val tree1: JTree = createTree(false)
    PopupHandler.installPopupHandler(tree1, group, ActionPlaces.CALL_HIERARCHY_VIEW_POPUP, ActionManager.getInstance)
    val forName: Class[_] = Class.forName("com.intellij.ide.hierarchy.CallHierarchyBrowserBase")
    val classes = forName.getDeclaredClasses
    var baseClass: Class[_] = null
    for (clazz <- classes if clazz.getName endsWith "BaseOnThisMethodAction") baseClass = clazz
    val constructor = baseClass.getConstructor()
    val inst: Any = constructor.newInstance()
    val methods = baseClass.getMethods
    val method = baseClass.getMethod("registerCustomShortcutSet", classOf[ShortcutSet], classOf[JComponent])
    method.invoke(inst, ActionManager.getInstance.getAction(IdeActions.ACTION_CALL_HIERARCHY).getShortcutSet, tree1)
    type2TreeMap.put(CALLEE_TYPE, tree1)
    val tree2: JTree = createTree(false)
    PopupHandler.installPopupHandler(tree2, group, ActionPlaces.CALL_HIERARCHY_VIEW_POPUP, ActionManager.getInstance)
    method.invoke(inst, ActionManager.getInstance.getAction(IdeActions.ACTION_CALL_HIERARCHY).getShortcutSet, tree2)
    type2TreeMap.put(CALLER_TYPE, tree2)
  }

  protected def getElementFromDescriptor(descriptor: HierarchyNodeDescriptor): PsiElement = {
    descriptor match {
      case nodeDescriptor: CallHierarchyNodeDescriptor => nodeDescriptor.getEnclosingElement
      case _ => null
    }
  }

  protected override def getOpenFileElementFromDescriptor(descriptor: HierarchyNodeDescriptor): PsiElement = {
    descriptor match {
      case nodeDescriptor: CallHierarchyNodeDescriptor => nodeDescriptor.getTargetElement
      case _ => null
    }
  }

  protected def isApplicableElement(element: PsiElement): Boolean = {
    element.isInstanceOf[PsiMethod]
  }

  protected def createHierarchyTreeStructure(typeName: String, psiElement: PsiElement): HierarchyTreeStructure = {
    if (CALLER_TYPE.equals(typeName))
      new ScalaCallerMethodsTreeStructure(myProject, psiElement.asInstanceOf[PsiMethod], getCurrentScopeType)
    else if (CALLEE_TYPE.equals(typeName))
      new ScalaCalleeMethodsTreeStructure(myProject, psiElement.asInstanceOf[PsiMethod], getCurrentScopeType)
    else null
  }

  protected def getComparator: Comparator[NodeDescriptor[_]] = {
    JavaHierarchyUtil.getComparator(myProject)
  }
}