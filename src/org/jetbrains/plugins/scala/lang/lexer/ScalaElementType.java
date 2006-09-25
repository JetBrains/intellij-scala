package org.jetbrains.plugins.scala.lang.lexer;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.ScalaLoader;

/**
 * Author: Ilya Sergey
 * Date: 24.09.2006
 * Time: 12:47:37
 */
public class ScalaElementType extends IElementType {
    private String debugName = null;

    public ScalaElementType(String debugName) {
        super(debugName, ScalaFileType.SCALA_FILE_TYPE.getLanguage());
        this.debugName = debugName;
    }

    public String toString(){
        return debugName;
    }
}
