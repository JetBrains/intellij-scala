package org.jetbrains.plugins.scala.lang.scaladoc.reflinks

import com.intellij.psi.tree.IElementType

object ScalaDocRefLinkElementTypes {
  val STRICT_MEMBER_ID: IElementType = new ScalaDocRefLinkElementType("STRICT_MEMBER_ID")
  val QUERY_SEGMENT: IElementType = new ScalaDocRefLinkElementType("QUERY_SEGMENT")
  val THIS_QUERY_SEGMENT: IElementType = new ScalaDocRefLinkElementType("THIS_QUERY_SEGMENT")
  val THIS_PACKAGE_SEGMENT: IElementType = new ScalaDocRefLinkElementType("THIS_PACKAGE_SEGMENT")
}

class ScalaDocRefLinkElementType(debugName: String) extends IElementType(debugName, ScalaDocRefLinkLanguage.INSTANCE) {
  override def toString: String = super.toString
}
