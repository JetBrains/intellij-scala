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
  import com.intellij.ide.util.treeView.smartTree.TreeElement
  import com.intellij.psi._

  import javax.swing._;
  import java.util.ArrayList;
  import java.util.List;

  import org.jetbrains.plugins.scala.lang.psi.ScalaFile
  import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
  import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
  import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
  import org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements._
  import org.jetbrains.plugins.scala.lang.psi.impl.top._, org.jetbrains.plugins.scala.lang.psi.impl.top.defs._
  import org.jetbrains.plugins.scala.lang.psi.impl.top.params._
  import org.jetbrains.plugins.scala.lang.psi.impl.patterns._

  /**
  * User: Dmitry.Krasilschikov
  * Date: 29.12.2006
  * Time: 17:50:40
  */

  class ScalaStructureViewElement(element: ScalaPsiElement) requires ScalaStructureViewElement extends StructureViewTreeElement {
    private val myElement: ScalaPsiElement = element

    override def getValue(): PsiElement = myElement.asInstanceOf[ScalaPsiElement]

    override def navigate(requestFocus: Boolean): Unit = (myElement.asInstanceOf[NavigationItem]).navigate(requestFocus)

    override def canNavigate(): Boolean = (myElement.asInstanceOf[NavigationItem]).canNavigate()

    override def canNavigateToSource(): Boolean = (myElement.asInstanceOf[NavigationItem]).canNavigateToSource()

    override def getChildren(): Array[TreeElement] = {
      var children = new ArrayBuffer[ScalaStructureViewElement]()
      var stmtStructureViewElements = new ArrayBuffer[ScalaStructureViewElement]()

      //todo: add inherited methods
      myElement match {
        case file: ScalaFile => {
          children ++= createStructureViewElements(file.getPackaging)
          children ++= createStructureViewElements(file.childrenOfType[ScTmplDef](ScalaElementTypes.TMPL_DEF_BIT_SET))
        }
        case packaging: ScPackaging  => {
          children ++= createStructureViewElements(packaging.getTmplDefs)
          children ++= createStructureViewElements(packaging.getInnerPackagings)
        }

        case topDef: ScTmplDef => {
          children ++= createStructureViewElements(topDef.getTmplDefs)

          val templateStmts = topDef.getTemplateStatements

          if (templateStmts != null) {
            stmtStructureViewElements ++= {
              val bigIter = for (val stmt <- templateStmts; stmt != null) yield elementAsDisjunctStructureViewElements(stmt)

              bigIter.flatMap(x => x)
            }
          }
        }
        case _ => {}
      }

      children ++= stmtStructureViewElements

      val resultAsArray: Array[TreeElement] = new Array(children.length)
      children.copyToArray(resultAsArray, 0)
      resultAsArray
    }

    private def createStructureViewElements[T  >: Null <: ScalaPsiElement]
      (elems : Iterable[T]) : Iterable[ScalaStructureViewElement] = elems.map(e => new ScalaStructureViewElement(e)) 

    override def getPresentation(): ItemPresentation = {
      new ItemPresentation() {
        def getPresentableText(): String = {
          ScalaStructureViewPresentationFormat.presentationText(myElement)
        }

        override def getTextAttributesKey(): TextAttributesKey = null
        override def getLocationString(): String = null
        override def getIcon(open: Boolean): Icon = myElement.getIcon(Iconable.ICON_FLAG_OPEN);
      }
    }

    private def patternDefVariables (pattern2: PsiElement, patDef : ScPatternDefinition) : Iterable[ScalaStructureViewElement] = {
      val varid = pattern2 match {
        case pat : ScPattern2Impl => if (pat.getVarid != null) pat.getVarid else pattern2
        case _ => pattern2
      }

      if (varid != null) Array(newStructureViewElement (varid, patDef))
      else Array()
    }

    private def createNewStructureViewElements (name : PsiElement, element : ScTemplateStatement) : Iterable[ScalaStructureViewElement] =
      element match {
        case patternDef : ScPatternDefinition => patternDefVariables (name, patternDef)
        case _ if (name != null) => Array(newStructureViewElement (name, element))
        case _ => Array()
      }

   private def newStructureViewElement (name : PsiElement, element : ScTemplateStatement) : ScalaStructureViewElement =

     new ScalaStructureViewElement(element) {
      override def getPresentation(): ItemPresentation = {
        new ItemPresentation() {
          override def getPresentableText(): String = {
            ScalaStructureViewPresentationFormat.presentationText(name.getText, element)
          }
          override def getTextAttributesKey(): TextAttributesKey = null
          override def getLocationString(): String = null
          override def getIcon(open: Boolean): Icon = myElement.getIcon(Iconable.ICON_FLAG_OPEN)
        }
      }
    }


    private def elementAsDisjunctStructureViewElements(element: ScTemplateStatement): Iterable[ScalaStructureViewElement] = {
      if (!element.isManyDeclarations) return createNewStructureViewElements(element.names.elements.next, element)

      val theNames = element.names
      val allElems = for (val name <- theNames) yield createNewStructureViewElements(name, element)

      allElems.flatMap(x => x)
    }
  }

}