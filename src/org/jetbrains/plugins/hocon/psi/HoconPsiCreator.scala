package org.jetbrains.plugins.hocon.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode

object HoconPsiCreator {

  import org.jetbrains.plugins.hocon.parser.HoconElementType._

  def createElement(ast: ASTNode) = ast.getElementType match {
    case Object => new HObject(ast)
    case ObjectEntries => new HObjectEntries(ast)
    case Include => new HInclude(ast)
    case Included => new HIncluded(ast)
    case ObjectField => new HObjectField(ast)
    case BareObjectField => new HBareObjectField(ast)
    case FieldPath | SubstitutionPath => new HPath(ast)
    case Key => new HKey(ast)
    case Array => new HArray(ast)
    case Substitution => new HSubstitution(ast)
    case Concatenation => new HConcatenation(ast)
    case UnquotedString => new HUnquotedString(ast)
    case String => new HString(ast)
    case Number => new HNumber(ast)
    case Null => new HNull(ast)
    case Boolean => new HBoolean(ast)
    case _ => new ASTWrapperPsiElement(ast)
  }

}
