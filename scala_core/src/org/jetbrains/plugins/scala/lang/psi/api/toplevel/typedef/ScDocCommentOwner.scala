package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

/** 
* @author ilyas
*/

import com.intellij.psi.PsiDocCommentOwner

trait ScDocCommentOwner extends PsiDocCommentOwner {

  def getDocComment = null

  // todo implement me!
  def isDeprecated = false

}