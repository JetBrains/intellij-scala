package org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements

import com.intellij.psi.tree._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.lang.psi.impl.primitives._
import org.jetbrains.plugins.scala.lang.psi.impl.types._
import org.jetbrains.plugins.scala.lang.psi.impl.top.params._

import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._

import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.DclDef
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.impl.types._
import org.jetbrains.plugins.scala.lang.typechecker._
import org.jetbrains.plugins.scala.lang.typechecker.types._

/**
*   Container for names, that could be referenced, sucha as value declaration,
*   binding, pattern, parameter declaration etc.
*/
trait ScReferenceIdContainer {

  /**
  *   Get explicit type from named element
  */
  def getAbstractType(refernceIdInstance: ScReferenceId): AbstractType = null

  /**
  *   Get explicit type from named element
  */
  def getExplicitType(refernceIdInstance: ScReferenceId): AbstractType = null

  /**
  *   Get infered type from named element
  */
  def getInferedType(refernceIdInstance: ScReferenceId): AbstractType = null

  /**
  *   returns list of labels for all variable or value  definitions
  */
  def getNames(): List[ScReferenceId]
}