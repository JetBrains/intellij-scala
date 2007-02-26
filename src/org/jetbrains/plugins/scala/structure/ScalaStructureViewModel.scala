package org.jetbrains.plugins.scala.structure;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.psi._;

import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.impl.top.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.impl.top.defs.ScTmplDef
import org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements.ScTemplateStatement
import org.jetbrains.plugins.scala.lang.psi.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.impl.top.templates.ScTemplateBody


/**
 * User: Dmitry.Krasilschikov
 * Date: 29.12.2006
 * Time: 17:41:27
 */

 /*
  * ScalaStructureViewModel is responsible for structure view organizing  
  */

class ScalaStructureViewModel (root : PsiElement) extends TextEditorBasedStructureViewModel (root.getContainingFile()) {
  var myRoot : PsiElement = root

  protected def getPsiFile() : PsiFile = myRoot.getContainingFile();

  override def getRoot() : ScalaStructureViewElement = new ScalaStructureViewElement(myRoot.asInstanceOf[ScalaPsiElement])

  [NotNull]
  override def getGroupers() : Array [Grouper] = Grouper.EMPTY_ARRAY

  [NotNull]
  override def getSorters() : Array[Sorter] = { val ar = new Array[Sorter](1); ar.update(0, Sorter.ALPHA_SORTER); ar }

  [NotNull]
  override def getFilters() : Array[Filter] = Filter.EMPTY_ARRAY;

  override def isSuitable(element : PsiElement) : Boolean  = {
    element match {
      case _ : ScPackaging => true
      case _ : ScTmplDef if (element.getParent.isInstanceOf[ScalaFile] || element.getParent.isInstanceOf[ScPackaging] || element.getParent.isInstanceOf[ScTmplDef]) => true
      case _ : ScTemplateStatement if (element.getParent.isInstanceOf[ScTemplateBody]) => true
      case _ => false
    }
  }
}
