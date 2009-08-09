package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package api


import _root_.org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.PsiComment

/**
* User: Alexander Podkhalyuzin
* Date: 22.07.2008
*/

trait ScDocComment extends PsiDocComment with ScalaPsiElement {

}