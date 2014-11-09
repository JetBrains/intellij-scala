package org.jetbrains.plugins.scala.hierarchy

import com.intellij.ide.hierarchy.call.JavaCallHierarchyProvider
import com.intellij.ide.hierarchy.{CallHierarchyBrowserBase, HierarchyBrowser}
import com.intellij.psi.{PsiElement, PsiMethod}

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaCallHierarchyProvider extends JavaCallHierarchyProvider {
  override def browserActivated(hierarchyBrowser: HierarchyBrowser): Unit = {
    (hierarchyBrowser.asInstanceOf[ScalaCallHierarchyBrowser]).changeView(CallHierarchyBrowserBase.CALLER_TYPE)
  }

  override def createHierarchyBrowser(target: PsiElement): HierarchyBrowser = {
    return new ScalaCallHierarchyBrowser(target.getProject, target.asInstanceOf[PsiMethod])
  }
}