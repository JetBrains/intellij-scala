package org.jetbrains.plugins.scala.lang.parser

import com.intellij.lang.DefaultASTFactoryImpl
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scalaDirective.psi.impl.ScDirectiveTokenImpl
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocElementType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.impl.ScPsiDocTokenImpl
import org.jetbrains.plugins.scalaDirective.lang.lexer.ScalaDirectiveElementType

/**
 * @note extending default cause it was the factory that was used by IDEA
 * @see [[com.intellij.psi.impl.source.tree.JavaASTFactory]]
 */
final class ScalaASTFactory extends DefaultASTFactoryImpl {

  override def createLeaf(typ: IElementType, text: CharSequence): LeafElement =
    typ match {
      case cliElement: ScalaDirectiveElementType =>
        new ScDirectiveTokenImpl(cliElement, text)
      case scDoc: ScalaDocElementType => new ScPsiDocTokenImpl(scDoc, text)
      case _                          => super.createLeaf(typ, text)
    }
}