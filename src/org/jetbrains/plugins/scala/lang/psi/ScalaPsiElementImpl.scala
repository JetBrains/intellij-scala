package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.openapi.util.Key
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable

import javax.swing.Icon

/**
  @author ven
*/
class ScalaPsiElementImpl ( node : ASTNode ) extends ASTWrapperPsiElement( node )
  with ScalaPsiElement {

    /*def childrenOfType[T >: Null <: ScalaPsiElementImpl] (tokSet : TokenSet) : Iterable[T] = new Iterable[T] () {
     def elements = new Iterator[T] () {
        private def findChild (child : ASTNode) : ASTNode = child match {
           case null => null
           case _ => if (tokSet.contains(child.getElementType())) child else findChild (child.getTreeNext)
        }

        var n : ASTNode = findChild (getNode.getFirstChildNode)

        def hasNext = n != null

        def next : T =  if (n == null) null else {
          val res = n
          n = findChild (n.getTreeNext)
          res.getPsi().asInstanceOf[T]
        }
      }
    }*/

    def childrenOfType[T >: Null <: PsiElement] (tokSet : TokenSet) : Iterable[T] = new Iterable[T] () {
     def elements = new Iterator[T] () {
        private def findChild (child : ASTNode) : ASTNode = child match {
           case null => null
           case _ => if (tokSet.contains(child.getElementType())) child else findChild (child.getTreeNext)
        }

        var n : ASTNode = findChild (getNode.getFirstChildNode)

        def hasNext = n != null

        def next : T =  if (n == null) null else {
          val res = n
          n = findChild (n.getTreeNext)
          res.getPsi().asInstanceOf[T]
        }
      }
    }

    def childSatisfyPredicateForPsiElement(predicate : PsiElement => Boolean) : PsiElement = {
      childSatisfyPredicateForPsiElement(predicate, getFirstChild, (e : PsiElement) => e.getNextSibling)
    }

    def childSatisfyPredicateForPsiElement(predicate : PsiElement => Boolean, startsWith : PsiElement) : PsiElement = {
      childSatisfyPredicateForPsiElement(predicate, startsWith, (e : PsiElement) => e.getNextSibling)
    }

    def childSatisfyPredicateForPsiElement(predicate : PsiElement => Boolean, startsWith : PsiElement, direction : PsiElement => PsiElement) : PsiElement = {
      def inner(e : PsiElement) : PsiElement = if (e == null || predicate(e)) e else inner(direction(e))

      inner(startsWith)
    }

    def childrenSatisfyPredicateForPsiElement[T >: Null <: ScalaPsiElementImpl](predicate : PsiElement => Boolean) = new Iterable[T] () {
     def elements = new Iterator[T] () {
        private def findChild (child : ASTNode) : ASTNode = child match {
           case null => null
           case _ => if (predicate(child.getPsi)) child else findChild (child.getTreeNext)
        }

        var n : ASTNode = findChild (getNode.getFirstChildNode)

        def hasNext = n != null

        def next : T =  if (n == null) null else {
          val res = n
          n = findChild (n.getTreeNext)
          res.getPsi().asInstanceOf[T]
        }
      }
    }

//    def childSatisfyPredicateFor(predicate : T => Boolean, startsWith : T) : PsiElement = {
//      childSatisfyPredicateForPsiElement(predicate, startsWith, (e : T) => e.getNextSibling)
//    }

    /*def childSatisfyPredicate[T](predicate : T => Boolean, startsWith : PsiElement, direction : T => T) : PsiElement = {

      def inner(curChild : PsiElement, e : T) : PsiElement = if (e == null || predicate(e)) e else inner(direction curChild, e)

      inner(startsWith, )
    }*/

    def childSatisfyPredicateForASTNode(predicate : ASTNode => Boolean) : PsiElement = {
      def inner(e : PsiElement) : PsiElement = if (e == null || predicate(e.getNode)) e else inner(e.getNextSibling)

      inner(getFirstChild)
    }

    def childSatisfyPredicateForElementType(predicate : IElementType => Boolean) : PsiElement = {
      def inner(e : PsiElement) : PsiElement = if (e == null || predicate (e.getNode.getElementType)) e else inner(e.getNextSibling)

      inner(getFirstChild)
    }


    def hasChild(elemType : IElementType) : Boolean = {
      return getChild(elemType) != null
    }

    [Nullable]
    def getChild(elemType : IElementType) : PsiElement = {
      getChild(elemType, getFirstChild, (e : PsiElement) => e.getNextSibling)
    }

    [Nullable]
    def getChild(elemType : IElementType, startsWith : PsiElement) : PsiElement = {
      getChild(elemType, startsWith, (e : PsiElement) => e.getNextSibling)
    }

    [Nullable]
    def getChild(elemType : IElementType, startsWith : PsiElement, direction : PsiElement => PsiElement) : PsiElement = {
      def inner (e : PsiElement) : PsiElement = e match {
         case null => null
         case _ => if (e.getNode.getElementType == elemType) e else inner (direction(e))
      }

      inner (startsWith)
   }

  override def replace(newElement : PsiElement) : PsiElement = {
    val parent : ScalaPsiElementImpl = getParent().asInstanceOf[ScalaPsiElementImpl]
    val parentNode = parent.getNode()
    val myNode = this.getASTNode()
    val newElementNode = newElement.asInstanceOf[ScalaPsiElementImpl].getASTNode()

    parentNode.replaceChild(myNode, newElementNode)
    newElement
  }

/*  override def copy() : PsiElement = {
    val parserDefinition : ParserDefinition = ScalaFileType.SCALA_FILE_TYPE.getLanguage.getParserDefinition
//    if (definition != null) ...
    val text = this.getText

    val dummyFile : PsiFile = manager.getElementFactory().createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), text)

    val classDef = dummyFile.getFirstChild
  }*/

  def getASTNode() : ASTNode = node

  override def toString : String = "scala psi element"
}