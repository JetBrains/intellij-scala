package org.jetbrains.plugins.scala
package lang.refactoring.rename

import com.intellij.psi._
import com.intellij.openapi.editor.Editor
import com.intellij.psi.search.SearchScope
import java.util

/**
 * Nikolay.Tropin
 * 6/18/13
 */
class ScalaClassInplaceRenamer(elementToRename: PsiNamedElement,
                               substituted: PsiElement,
                               editor: Editor)
        extends ScalaMemberInplaceRenamer(elementToRename, substituted, editor) {

  override def collectRefs(referencesSearchScope: SearchScope): util.Collection[PsiReference] = {
    import scala.collection.JavaConverters._
    val allRefs = new RenameScalaClassProcessor().findReferences(elementToRename).asScala
    def isSameFile(ref: PsiReference): Boolean = {
      if (ref != null) {
        val element = ref.getElement
        element != null && element.isValid && !notSameFile(null, element.getContainingFile)
      } else false
    }
    allRefs.filter(isSameFile).asJavaCollection
  }

}