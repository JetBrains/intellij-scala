package intellijhocon
package psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode

sealed abstract class HoconPsiElement(ast: ASTNode) extends ASTWrapperPsiElement(ast)

sealed trait HValue extends HoconPsiElement

sealed trait HLiteral extends HValue

sealed trait HObjectEntry extends HoconPsiElement

final class HObjectEntries(ast: ASTNode) extends HoconPsiElement(ast)

final class HObjectField(ast: ASTNode) extends HoconPsiElement(ast) with HObjectEntry

final class HBareObjectField(ast: ASTNode) extends HoconPsiElement(ast)

final class HInclude(ast: ASTNode) extends HoconPsiElement(ast) with HObjectEntry

final class HIncluded(ast: ASTNode) extends HoconPsiElement(ast)

final class HKey(ast: ASTNode) extends HoconPsiElement(ast)

final class HPath(ast: ASTNode) extends HoconPsiElement(ast)

final class HObject(ast: ASTNode) extends HoconPsiElement(ast) with HValue

final class HArray(ast: ASTNode) extends HoconPsiElement(ast) with HValue

final class HSubstitution(ast: ASTNode) extends HoconPsiElement(ast) with HValue

final class HConcatenation(ast: ASTNode) extends HoconPsiElement(ast) with HValue

final class HNull(ast: ASTNode) extends HoconPsiElement(ast) with HLiteral

final class HBoolean(ast: ASTNode) extends HoconPsiElement(ast) with HLiteral

final class HNumber(ast: ASTNode) extends HoconPsiElement(ast) with HLiteral

final class HUnquotedString(ast: ASTNode) extends HoconPsiElement(ast)

final class HString(ast: ASTNode) extends HoconPsiElement(ast) with HLiteral
