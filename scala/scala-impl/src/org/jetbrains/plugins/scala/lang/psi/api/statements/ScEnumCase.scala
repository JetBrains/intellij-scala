package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScConstructorOwner, ScTypeDefinition}

trait ScEnumCase extends ScTypeDefinition
  with ScConstructorOwner
  with ScDeclaredElementsHolder