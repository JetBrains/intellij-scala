package org.jetbrains.plugins.scala.structure;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.psi._;

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement


/**
 * User: Dmitry.Krasilschikov
 * Date: 29.12.2006
 * Time: 17:41:27
 */


class ScalaStructureViewModel (root : PsiElement) extends TextEditorBasedStructureViewModel (root.getContainingFile()) {
  var myRoot : PsiElement = root

  protected def getPsiFile() : PsiFile = myRoot.getContainingFile();

//  final val SUITABLE_CLASSES : Array[Class] = Array.apply(ScTmplDef)


  //is Element suitable
//  override def isElementSuitable = null

  override def getRoot() : ScalaStructureViewElement = new ScalaStructureViewElement(myRoot)

  //@NotNull
  override def getGroupers() : Array [Grouper] = Grouper.EMPTY_ARRAY

//  @NotNull
  override def getSorters() : Array[Sorter] = { val ar = new Array[Sorter](1); ar.update(0, Sorter.ALPHA_SORTER); ar }

//  @NotNull
  override def getFilters() : Array[Filter] = Filter.EMPTY_ARRAY;

}
