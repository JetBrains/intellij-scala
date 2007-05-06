package org.jetbrains.plugins.scala.lang.psi.impl.types

/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.types._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.lang.psi.impl.patterns._
import org.jetbrains.plugins.scala.lang.resolve.references._
import org.jetbrains.plugins.scala.lang.psi.impl.types._
import org.jetbrains.plugins.scala.lang.psi.impl.primitives._
import org.jetbrains.plugins.scala.lang.psi.impl.top.templates._
import org.jetbrains.plugins.scala.lang.psi.impl.patterns._

trait ScStableId extends ScPattern3

class ScStableIdImpl(node: ASTNode) extends ScSimpleExprImpl(node) with ScStableId {

  override def toString: String = "Stable Identifier"

  override def getReference = {
    if (! getText.contains(".") &&
    getParent != null &&
    (getParent.isInstanceOf[ScalaType] ||
    getParent.isInstanceOf[ScCompoundTypeImpl] ||
    getParent.isInstanceOf[ScConstructor] ||
    getParent.isInstanceOf[ScRequiresBlock] ||
    getParent.isInstanceOf[ScTypePatternArgsImpl] ||
    getParent.isInstanceOf[ScSimpleTypePatternImpl] ||
    getParent.isInstanceOf[ScSimpleTypePattern1Impl] ||
    getParent.isInstanceOf[ScTypePatternImpl] ||
    getParent.isInstanceOf[ScPattern1Impl] ||
    getParent.isInstanceOf[ScConstructor] ||
    getParent.isInstanceOf[ScTemplateParents] ||
    getParent.isInstanceOf[ScMixinParents])) {
      new ScalaClassReference(this)  // Class or Trait reference
    } else
    // TODO Remove "."

      new ScalaLocalReference(this)  // local reference

  }

  override def getName = getText
}