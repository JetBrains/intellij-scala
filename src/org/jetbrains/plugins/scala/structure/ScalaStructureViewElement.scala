/*
import scala.collection.mutable.LinkedList
import scala.Nil
import scala.{List => scalaList}
import scala.collection.mutable.ArrayBuffer

package org.jetbrains.plugins.scala.structure {

import com.intellij.navigation.NavigationItem
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Iconable;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.RowIcon;

import javax.swing._;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.containers.ScContainer
import org.jetbrains.plugins.scala.lang.psi.visitors.ScalaContainerVisitor
import org.jetbrains.plugins.scala.lang.psi.visitors.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.impl.top._, org.jetbrains.plugins.scala.lang.psi.impl.top.defs._
*/

/**
 * User: Dmitry.Krasilschikov
 * Date: 29.12.2006
 * Time: 17:50:40
 */

 /*
class ScalaStructureViewElement (element : ScalaPsiElement) extends StructureViewTreeElement[ScalaPsiElement] {
  private var myElement : ScalaPsiElement = element

  def getValue() : ScalaPsiElement = myElement

  def navigate(requestFocus : Boolean) : Unit = (myElement.asInstanceOf[NavigationItem]).navigate(requestFocus);

  def canNavigate() : Boolean = (myElement.asInstanceOf[NavigationItem]).canNavigate()

  def canNavigateToSource() : Boolean = (myElement.asInstanceOf[NavigationItem]).canNavigateToSource()

  override def getChildren() : Array[StructureViewTreeElement[ScalaPsiElement]] = {
    var childrenElements: ArrayBuffer[ScalaPsiElement] = new ArrayBuffer[ScalaPsiElement]();
 */
   /* val myVisitor : ScalaContainerVisitor = new ScalaContainerVisitor() {
    
      def visitContainer(scContainer : ScContainer) : Unit = {
        scContainer match {
          case packaging : ScPackaging => {
            childrenElements.insertAll(0, packaging.getTmplDefs)
          }
          case _ => {}
        }
            childrenElements.addAll(scContainer.getSubContainers());
      }
    };

    myElement.accept(myVisitor);
    */

//    val childrenElements : Array[] = new Array[StructureViewTreeElement](1)
/*
    
    if (myElement.isInstanceOf[ScTmplDef])
      childrenElements(0) = myElement.asInstanceOf[ScTmplDef].getTemplate.asInstanceOf[ScalaPsiElement]

    for ( val i <- Array.range(0, childrenElements.length)) yield new ScalaStructureViewElement(childrenElements.apply(i))
  }


  override def getPresentation() : ItemPresentation = {
        new ItemPresentation() {
             def getPresentableText() : String = {
                return myElement.getText();
            }

             override def getTextAttributesKey() : TextAttributesKey = null

             override def getLocationString() : String  = null

             override def getIcon(open : Boolean) : Icon = myElement.getIcon(Iconable.ICON_FLAG_OPEN);
        }
    }

}

}*/