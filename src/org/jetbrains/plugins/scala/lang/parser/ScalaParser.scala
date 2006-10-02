package org.jetbrains.plugins.scala.lang.parser

import com.intellij.lang.PsiParser
import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.PROGRAM;

class ScalaParser extends PsiParser {

    def parse(root : IElementType, builder : PsiBuilder ) : ASTNode = {
        (new PROGRAM()).parse(builder);
        return builder.getTreeBuilt();
    }
}
