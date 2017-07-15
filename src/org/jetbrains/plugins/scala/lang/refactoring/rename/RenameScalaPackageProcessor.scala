package org.jetbrains.plugins.scala
package lang.refactoring.rename

import java.util

import com.intellij.psi.{PsiElement, PsiPackage}
import com.intellij.refactoring.rename.RenamePsiPackageProcessor
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.extensions.PsiElementExt

/**
 * @author Alefas
 * @since 06.11.12
 */
class RenameScalaPackageProcessor extends RenamePsiPackageProcessor with ScalaRenameProcessor {
  override def prepareRenaming(element: PsiElement, newName: String, allRenames: util.Map[PsiElement, String]) {
    element match {
      case p: PsiPackage =>
        val po = ScalaShortNamesCacheManager.getInstance(element.getProject).getPackageObjectByName(p.getQualifiedName, element.resolveScope)
        if (po != null && po.name != "`package`") {
          allRenames.put(po, newName)
        }
      case _ =>
    }
  }
}
