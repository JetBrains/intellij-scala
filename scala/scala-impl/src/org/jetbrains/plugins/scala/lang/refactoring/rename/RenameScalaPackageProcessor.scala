package org.jetbrains.plugins.scala
package lang.refactoring.rename

import com.intellij.psi.search.SearchScope
import com.intellij.psi.{PsiElement, PsiPackage, PsiReference}
import com.intellij.refactoring.rename.RenamePsiPackageProcessor
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.extensions.PsiElementExt

import java.{util => ju}

class RenameScalaPackageProcessor extends RenamePsiPackageProcessor with ScalaRenameProcessor {

  override def findReferences(element: PsiElement,
                              searchScope: SearchScope,
                              searchInCommentsAndStrings: Boolean): ju.Collection[PsiReference] =
    super[RenamePsiPackageProcessor].findReferences(element, searchScope, searchInCommentsAndStrings)

  override def prepareRenaming(element: PsiElement,
                               newName: String,
                               allRenames: ju.Map[PsiElement, String]): Unit = element match {
    case p: PsiPackage =>
      for {
        packageObject <- ScalaShortNamesCacheManager.getInstance(element.getProject)
          .findPackageObjectByName(p.getQualifiedName, element.resolveScope)
        if packageObject.name != "`package`"
      } allRenames.put(packageObject, newName)
    case _ =>
  }
}
