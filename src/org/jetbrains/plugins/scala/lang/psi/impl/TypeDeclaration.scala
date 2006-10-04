package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.lang.ASTNode
/**
 * User: Dmitry.Krasilschikov
 * Date: 03.10.2006
 * Time: 19:43:50
 */
class TypeDeclaration{
  abstract class ClassBaseDeclaration extends TypeDeclaration
    case class ScObject( node : ASTNode ) extends ClassBaseDeclaration
    case class ScTrait( node : ASTNode ) extends ClassBaseDeclaration
    case class ScClass( node : ASTNode ) extends ClassBaseDeclaration

  }

