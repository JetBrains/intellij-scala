package org.jetbrains.plugins.scala.lang.refactoring.rename

import com.intellij.refactoring.rename.RenameJavaClassProcessor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import java.util.Map
import java.lang.String

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.09.2009
 */

class RenameScalaClassProcessor extends RenameJavaClassProcessor {
  override def canProcessElement(element: PsiElement): Boolean = element.isInstanceOf[ScTypeDefinition]

  override def prepareRenaming(element: PsiElement, newName: String, allRenames: Map[PsiElement, String]): Unit = {
    //for Scala it's not interesting to rename constructors
  }
}