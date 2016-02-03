package org.jetbrains.plugins.hocon.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.parser.PsiCreator

object HoconPsiCreator extends PsiCreator {

  import org.jetbrains.plugins.hocon.parser.HoconElementType._

  def createElement(ast: ASTNode) = ast.getElementType match {
    case Object => new HObject(ast)
    case ObjectEntries => new HObjectEntries(ast)
    case Include => new HInclude(ast)
    case Included => new HIncluded(ast)
    case ObjectField => new HObjectField(ast)
    case PrefixedField => new HPrefixedField(ast)
    case ValuedField => new HValuedField(ast)
    case Path => new HPath(ast)
    case Key => new HKey(ast)
    case Array => new HArray(ast)
    case Substitution => new HSubstitution(ast)
    case Concatenation => new HConcatenation(ast)
    case UnquotedString => new HUnquotedString(ast)
    case StringValue => new HStringValue(ast)
    case KeyPart => new HKeyPart(ast)
    case IncludeTarget => new HIncludeTarget(ast)
    case Number => new HNumber(ast)
    case Null => new HNull(ast)
    case Boolean => new HBoolean(ast)
    case _ => new ASTWrapperPsiElement(ast)
  }

}
