package org.jetbrains.plugins.scala.lang.scaladoc.lexer;

import org.jetbrains.plugins.scala.ScalaFileType;
import com.intellij.psi.tree.IElementType;

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */
public class ScalaDocElementType extends IElementType {
  private String debugName = null;

  public ScalaDocElementType(String debugName) {
    super(debugName, ScalaFileType.SCALA_FILE_TYPE.getLanguage());
    this.debugName = debugName;
  }

  public String toString() {
    return debugName;
  }
}
