package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.PsiElement

import scala.util.parsing.combinator._

/**
 * Pavel.Fatin, 11.05.2010
 */

class PsiElementMock(val name: String, children: PsiElementMock*) extends AbstractPsiElementMock {
  private var parent: PsiElement = _
  private var prevSibling: PsiElement = _
  private var nextSibling: PsiElement = _
  private var firstChild: PsiElement = children.headOption.orNull
  private var lastChild: PsiElement = children.lastOption.orNull
  
  
  for(child <- children) { child.parent = this }
  
  if(children.nonEmpty) {
    for((a, b) <- children.zip(children.tail)) {
      a.nextSibling = b
      b.prevSibling = a
    }
  }

  override def getParent = parent

  override def getContext = parent

  override def getPrevSibling = prevSibling

  override def getNextSibling = nextSibling

  override def getChildren = children.toArray

  override def getFirstChild = firstChild

  override def getLastChild = lastChild

  override def toString = name
  
  override def getText: String = {
    if(children.isEmpty) 
      toString 
    else 
      toString + "(" + children.map(_.getText).mkString(", ") + ")"
  }
}

object PsiElementMock extends JavaTokenParsers {
  def apply(name: String, children: PsiElementMock*) = new PsiElementMock(name, children: _*)

  def parse(s: String): PsiElementMock = parse(element, s).get
 
  private def element: Parser[PsiElementMock] = identifier~opt(elements) ^^ {
      case name~children => PsiElementMock(name, children.getOrElse(Nil): _*)
  } 
 
  private def identifier: Parser[String] = """[^,() ]+""".r
    
  private def elements: Parser[List[PsiElementMock]] = "("~>repsep(element, ",")<~")"
}
