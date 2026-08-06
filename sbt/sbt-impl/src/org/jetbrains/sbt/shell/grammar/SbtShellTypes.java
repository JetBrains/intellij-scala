// This is a generated file. Not intended for manual editing.
package org.jetbrains.sbt.shell.grammar;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.sbt.shell.SbtShellElementType;
import org.jetbrains.sbt.shell.SbtShellTokenType;
import org.jetbrains.sbt.shell.grammar.impl.*;

public interface SbtShellTypes {

  IElementType COMMAND = new SbtShellElementType("COMMAND");
  IElementType CONFIG = new SbtShellElementType("CONFIG");
  IElementType INTASK = new SbtShellElementType("INTASK");
  IElementType KEY = new SbtShellElementType("KEY");
  IElementType PARAMS = new SbtShellElementType("PARAMS");
  IElementType PROJECT_ID = new SbtShellElementType("PROJECT_ID");
  IElementType SCOPED_KEY = new SbtShellElementType("SCOPED_KEY");
  IElementType URI = new SbtShellElementType("URI");

  IElementType ANYCHAR = new SbtShellTokenType("ANYCHAR");
  IElementType CLOSE_BRACE = new SbtShellTokenType("}");
  IElementType COLON = new SbtShellTokenType(":");
  IElementType DOUBLE_COLON = new SbtShellTokenType("::");
  IElementType ID = new SbtShellTokenType("ID");
  IElementType OPEN_BRACE = new SbtShellTokenType("{");
  IElementType SEMICOLON = new SbtShellTokenType(";");
  IElementType SLASH = new SbtShellTokenType("/");
  IElementType URISTRING = new SbtShellTokenType("URISTRING");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
       if (type == COMMAND) {
        return new SbtShellCommandImpl(node);
      }
      else if (type == CONFIG) {
        return new SbtShellConfigImpl(node);
      }
      else if (type == INTASK) {
        return new SbtShellIntaskImpl(node);
      }
      else if (type == KEY) {
        return new SbtShellKeyImpl(node);
      }
      else if (type == PARAMS) {
        return new SbtShellParamsImpl(node);
      }
      else if (type == PROJECT_ID) {
        return new SbtShellProjectIdImpl(node);
      }
      else if (type == SCOPED_KEY) {
        return new SbtShellScopedKeyImpl(node);
      }
      else if (type == URI) {
        return new SbtShellUriImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
