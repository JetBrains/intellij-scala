package org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax;

import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocElementType;

/**
 * User: Dmitry Naidanov
 * Date: 29.10.11
 */
public class ScaladocSyntaxElementType extends ScalaDocElementType{
  private final int flagConst;

  public ScaladocSyntaxElementType(String debugName, int flagConst) {
    super(debugName);
    this.flagConst = flagConst;
  }

  public int getFlagConst() {
    return flagConst;
  }

  @Override
  public String toString() {
    return super.toString() + " " + (~(getFlagConst() - 1) & getFlagConst());
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || obj.getClass() != this.getClass()) {
      return false;
    }
    ScaladocSyntaxElementType elementType = (ScaladocSyntaxElementType) obj;
    return elementType.getFlagConst() == this.getFlagConst();
  }

  @Override
  public int hashCode() {
    return ~(flagConst - 1) & flagConst;
  }
}
