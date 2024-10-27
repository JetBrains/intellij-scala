package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDeclaration
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition


/**
 *  For every abstract type with context bounds inject a corresponding given instance.
 *  e.g.
 *  {{{
 *    trait Sorted {
 *      type Element : Ord as ord
 *    }
 *  }}}
 *  is expanded to
 *  {{{
 *    trait Sorted {
 *      type Element
 *      given ord: Ord[Element] = scala.compiletime.deferred
 *    }
 *  }}}
 */
class AbstractTypeContextBoundsInjector extends SyntheticMembersInjector {
  override def injectFunctions(source: ScTypeDefinition): Seq[String] = {
    val typesWithContextBounds = source.aliases.collect {
      case tdecl: ScTypeAliasDeclaration if tdecl.contextBounds.nonEmpty => tdecl
    }

    typesWithContextBounds.flatMap { typeDecl =>
      val bounds = typeDecl.contextBounds
      bounds.map { cb =>
        val optionalName = cb.name.fold("")(_ + ": ")
        val tpe          = s"(${cb.typeElement.getText})[${typeDecl.name}]"
        s"given $optionalName$tpe = scala.compiletime.deferred"
      }
    }
  }
}
