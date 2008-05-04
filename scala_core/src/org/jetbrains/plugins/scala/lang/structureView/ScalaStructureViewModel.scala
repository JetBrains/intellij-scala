package org.jetbrains.plugins.scala.lang.structureView

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel
import com.intellij.ide.util.treeView.smartTree._
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.structureView.elements.impl._

/**
* @author Alexander.Podkhalyuz
* Date: 04.05.2008
*/

class ScalaStructureViewModel(private val myRootElement: ScalaFile) extends TextEditorBasedStructureViewModel(myRootElement) {

  protected def getPsiFile(): PsiFile = {
    return myRootElement;
  }

  @NotNull
  def getRoot(): StructureViewTreeElement = {
    return new ScalaFileStructureViewElement(myRootElement);
  }

  @NotNull
  def getGroupers(): Array[Grouper] = {
    return Grouper.EMPTY_ARRAY;
  }

  @NotNull
 def getSorters(): Array[Sorter] = {
    val res = new Array[Sorter](1)
    res(0) = Sorter.ALPHA_SORTER
    return res
  }

  @NotNull
  def getFilters(): Array[Filter] = {
    return Filter.EMPTY_ARRAY;
  }
}