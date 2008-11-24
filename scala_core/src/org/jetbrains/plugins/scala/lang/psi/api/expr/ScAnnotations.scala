package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiReferenceList
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScAnnotations extends ScalaPsiElement with PsiReferenceList {

  def getReferenceElements = Array[PsiJavaCodeReferenceElement]()

  def getReferencedTypes = Array[PsiClassType]()

  //todo return appropriate roles
  def getRole = PsiReferenceList.Role.THROWS_LIST

  def getAnnotations: Array[ScAnnotation] = findChildrenByClass(classOf[ScAnnotation])
}