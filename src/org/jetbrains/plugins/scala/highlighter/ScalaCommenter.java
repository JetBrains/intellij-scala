package org.jetbrains.plugins.scala.highlighter;

import com.intellij.lang.Commenter;
import org.jetbrains.annotations.Nullable;

/**
 * Author: Ilya Sergey
 * Date: 29.09.2006
 * Time: 20:48:30
 */
public class ScalaCommenter implements Commenter {
  public String getLineCommentPrefix() {
    return "//";
  }

  public boolean isLineCommentPrefixOnZeroColumn() {
    return false;
  }

  public String getBlockCommentPrefix() {
    return "/*";
  }

  public String getBlockCommentSuffix() {
    return "*/";
  }
}
