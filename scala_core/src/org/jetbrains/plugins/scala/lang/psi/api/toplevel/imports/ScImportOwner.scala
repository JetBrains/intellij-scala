package org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports

/**
 * User: Alexander Podkhalyuzin
 * Date: 04.09.2008
 */

trait ScImportOwner extends ScalaPsiElement {
  //todo[Sasha] rewrite using ScalaElementTypes
  def deleteImportStmt(stmt: ScImportStmt): Unit = {
    val remove = getNode.removeChild _
    val node = stmt.getNode
    val next = node.getTreeNext
    if (next == null) {
      remove(node)
    }
    else if (next.getText.indexOf("\n") != -1) {
      remove(next)
      remove(node)
    } else if (next.getText.charAt(0) == ';') {
      val nextnext = next.getTreeNext
      if (nextnext == null) {
        remove(next)
        remove(node)
      }
      else if (next.getText.indexOf("\n") != -1) {
        remove(nextnext)
        remove(next)
        remove(node)
      } else {
        remove(node)
        remove(next)
      }
    }
    else {
      remove(node)
    }
  }
}