package org.jetbrains.plugins.scala.lang.psi.util

import org.jetbrains.plugins.scala.finder.ScalaSourceFilterScope
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.project.Project
import com.intellij.psi.{JavaPsiFacade, PsiManager, PsiClass}
import collection.mutable.{HashMap, ArrayBuffer}

/**
 * @author Alexander Podkhalyuzin
 */

object CommonClassesSearcher {
  import collection.mutable.WeakHashMap
  private val cachedClasses: WeakHashMap[(Project, String), Seq[PsiClass]] = new WeakHashMap[(Project, String), Seq[PsiClass]]
  private val modCount: WeakHashMap[(Project, String), Long] = new WeakHashMap[(Project, String), Long]

  def getCachedClass(manager: PsiManager, scope: GlobalSearchScope, fqn: String): Seq[PsiClass] = {
    var res: Seq[PsiClass] = cachedClasses.get(manager.getProject, fqn).getOrElse(null)
    val count = manager.getModificationTracker.getJavaStructureModificationCount
    val count1: Option[Long] = modCount.get((manager.getProject, fqn))
    if (res == null || count1 == null || count != count1.get) {
      res = getCachedClassImpl(manager, fqn)
      cachedClasses((manager.getProject, fqn)) = res
      modCount((manager.getProject, fqn)) = count
    }
    val filter = new ScalaSourceFilterScope(scope, manager.getProject)
    return res.filter(c => filter.contains(c.getContainingFile.getVirtualFile))
  }

  private def getCachedClassImpl(manager: PsiManager, fqn: String): Seq[PsiClass] = {
    val res = new ArrayBuffer[PsiClass]
    res ++= JavaPsiFacade.getInstance(manager.getProject).
      findClasses(fqn, GlobalSearchScope.allScope(manager.getProject))
    res.toSeq
  }
}