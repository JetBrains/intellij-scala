
package org.jetbrains.plugins.scala.structure;

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.psi._;
import org.jetbrains.annotations._

/**
 * User: Dmitry.Krasilschikov
 * Date: 29.12.2006
 * Time: 17:39:45
 */

/*
 * ScalaStructureViewBuilder creates model
 */


class ScalaStructureViewBuilder ( psiFile : PsiFile ) extends TreeBasedStructureViewBuilder {
    private var myPsiFile : PsiFile = psiFile

    [NotNull]
    override def createStructureViewModel() : StructureViewModel =
      new ScalaStructureViewModel(myPsiFile.asInstanceOf[PsiElement])
}

