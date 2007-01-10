/*package org.jetbrains.plugins.scala.structure;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.psi.PsiFile;

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
*/
/**
 * User: Dmitry.Krasilschikov
 * Date: 29.12.2006
 * Time: 17:41:27
 */

/*
class ScalaStructureViewModel (root : ScalaPsiElement) extends TextEditorBasedStructureViewModel (root.getContainingFile()) {
  var myRoot : ScalaPsiElement = root

//  def this (root : ScalaPsiElement) = {
//    this(root)
//    var myRoot : ScalaPsiElement = root
//  }

  protected def getPsiFile() : PsiFile = myRoot.getContainingFile();

//  final val SUITABLE_CLASSES : Array[Class] = Array.apply(ScTmplDef)

  override protected def getSuitableClasses() : Array[Class[Object]] = {
    new Array[Class[Object]](0)
  }

  override def getRoot() : StructureViewTreeElement[ScalaPsiElement] = new ScalaStructureViewElement(myRoot)

  //@NotNull
  override def getGroupers() : Array [Grouper] = Grouper.EMPTY_ARRAY

//  @NotNull
  override def getSorters() : Array[Sorter] = { val ar = new Array[Sorter](1); ar.update(0, Sorter.ALPHA_SORTER); ar }

//  @NotNull
  override def getFilters() : Array[Filter] = Filter.EMPTY_ARRAY;

}
*/