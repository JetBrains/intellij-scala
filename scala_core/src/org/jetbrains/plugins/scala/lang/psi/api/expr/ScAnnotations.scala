package org.jetbrains.plugins.scala.lang.psi.api.expr

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScAnnotations extends ScalaPsiElement with PsiReferenceList {

  def getReferenceElements = Array[PsiJavaCodeReferenceElement]()

  // todo implement for Java throws clauses
  def getReferencedTypes = Array[PsiClassType]()

  //todo return appropriate roles
  def getRole = PsiReferenceList.Role.THROWS_LIST

  def getAnnotations: Array[ScAnnotation] = findChildrenByClass(classOf[ScAnnotation])
}