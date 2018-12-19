package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.impl.source.tree.FileElement
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.{PsiClass, PsiCompiledElement}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

//this marker trait is required for Show Bytecode action
trait ScCompiledClass extends PsiCompiledElement {
  this: ScTypeDefinition =>

  override def getMirror: PsiClass = getSourceMirrorClass
}

object ScCompiledClass {

  def sourceOrCompiled[T](node: ASTNode)(source: => T, compiled: => T): T = {
    if (isInCompiledFile(node)) compiled else source
  }

  def sourceOrCompiled[T](stub: StubElement[_])(source: => T, compiled: => T): T = {
    if (isInCompiledFile(stub)) compiled else source
  }

  private def isInCompiledFile(stub: StubElement[_]): Boolean = {
    var parent: StubElement[_] = stub

    do {
      parent match {
        case scFileStub: AbstractFileStub => return scFileStub.isCompiled
        case _ =>
      }
      parent = parent.getParentStub
    } while (parent != null)

    false
  }

  private def isInCompiledFile(node: ASTNode): Boolean = {
    var parent = node

    do {
      parent match {
        case fileElement: FileElement =>
          return fileElement.getPsi match {
            case f: ScalaFile => f.isCompiled
            case _ => false
          }
        case _ =>
      }
      parent = parent.getTreeParent
    } while (parent != null)

    false
  }
}