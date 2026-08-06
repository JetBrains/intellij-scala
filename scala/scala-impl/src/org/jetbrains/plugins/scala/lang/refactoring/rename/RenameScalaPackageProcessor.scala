package org.jetbrains.plugins.scala.lang.refactoring.rename

import com.intellij.openapi.editor.Editor
import com.intellij.psi.search.SearchScope
import com.intellij.psi.{PsiElement, PsiPackage, PsiReference}
import com.intellij.refactoring.rename.RenamePsiPackageProcessor
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScEnd
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScEndImpl.Target
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScObjectImpl

import java.{util => ju}

class RenameScalaPackageProcessor extends RenamePsiPackageProcessor with ScalaRenameProcessor {
  private def substituteEnd(element: PsiElement): PsiElement = element match {
    case Target(end: ScEnd) =>
      end.begin
        .flatMap(_.asOptionOfUnsafe[ScPackaging])
        .flatMap(p => ScPackageImpl.findPackage(element.getProject, p.fullPackageName))
        .getOrElse(element)
    case _ => element
  }

  override def canProcessElement(element: PsiElement): Boolean =
    super.canProcessElement(substituteEnd(element))

  override def substituteElementToRename(element: PsiElement, editor: Editor): PsiElement =
    super.substituteElementToRename(substituteEnd(element), editor)


  override def findReferences(element: PsiElement,
                              searchScope: SearchScope,
                              searchInCommentsAndStrings: Boolean): ju.Collection[PsiReference] = {
    val references = super[RenamePsiPackageProcessor].findReferences(element, searchScope, searchInCommentsAndStrings)
    // also find end markers, which are normally omitted from being found
    for {
      pack <- element.asOptionOf[PsiPackage]
      end <- ScPackageImpl.getAllEndMarkers(pack)
    } references.add(end)

    references
  }

  override def prepareRenaming(element: PsiElement,
                               newName: String,
                               allRenames: ju.Map[PsiElement, String]): Unit = element match {
    case p: PsiPackage =>
      val manager = ScalaShortNamesCacheManager.getInstance(element.getProject)
      val packageObjects = manager.findPackageObjectByName(p.getQualifiedName, element.resolveScope)
      for {
        packageObject <- packageObjects
        if packageObject.name != ScObjectImpl.LegacyPackageObjectNameInBackticks
      } allRenames.put(packageObject, newName)
    case _ =>
  }
}
