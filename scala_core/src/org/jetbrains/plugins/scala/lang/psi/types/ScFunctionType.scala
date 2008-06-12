package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaration

/** 
* @author ilyas
*/

case class ScFunctionType(returnType: ScType, params: Seq[ScType]) extends ScType

case class ScTupleType(components : Seq[ScType]) extends ScType

case class ScCompoundType(components : Seq[ScType], decls : Seq[ScDeclaration], typeDecls : Seq[ScTypeAlias])