package org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocElementType;
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType;

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
  
  public static boolean canClose(IElementType opening, IElementType closing) {
    if (opening == ScalaDocTokenType.DOC_INNER_CODE_TAG && closing == ScalaDocTokenType.DOC_INNER_CLOSE_CODE_TAG)
      return true;
    if (opening.getClass() != ScaladocSyntaxElementType.class || closing.getClass() != ScaladocSyntaxElementType.class)
      return false;

    if (opening == ScalaDocTokenType.DOC_LINK_TAG || opening == ScalaDocTokenType.DOC_HTTP_LINK_TAG) {
      return closing == ScalaDocTokenType.DOC_LINK_CLOSE_TAG;
    } else if (opening == ScalaDocTokenType.VALID_DOC_HEADER) {
      return (closing == ScalaDocTokenType.DOC_HEADER) || (closing == ScalaDocTokenType.VALID_DOC_HEADER);
    } else return opening == closing;
  }
}
