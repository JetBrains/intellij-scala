package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

/** 
* @author ilyas
*/

import com.intellij.psi._

trait ScDocCommentOwner extends PsiDocCommentOwner with ScMember {

  def getDocComment = null

  // todo implement me!
  def isDeprecated = false

}