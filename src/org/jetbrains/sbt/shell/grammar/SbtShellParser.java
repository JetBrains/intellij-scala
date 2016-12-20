// This is a generated file. Not intended for manual editing.
package org.jetbrains.sbt.shell.grammar;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LightPsiParser;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;

import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import static org.jetbrains.sbt.shell.grammar.SbtShellTypes.*;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class SbtShellParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType t, PsiBuilder b) {
    parseLight(t, b);
    return b.getTreeBuilt();
  }

  public void parseLight(IElementType t, PsiBuilder b) {
    boolean r;
    b = adapt_builder_(t, b, this, null);
    Marker m = enter_section_(b, 0, _COLLAPSE_, null);
    if (t == COMMAND) {
      r = command(b, 0);
    }
    else if (t == CONFIG) {
      r = config(b, 0);
    }
    else if (t == INTASK) {
      r = intask(b, 0);
    }
    else if (t == KEY) {
      r = key(b, 0);
    }
    else if (t == PROJECT_ID) {
      r = projectId(b, 0);
    }
    else if (t == SCOPED_KEY) {
      r = scopedKey(b, 0);
    }
    else if (t == URI) {
      r = uri(b, 0);
    }
    else {
      r = parse_root_(t, b, 0);
    }
    exit_section_(b, 0, m, t, r, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType t, PsiBuilder b, int l) {
    return sbtShellLine(b, l + 1);
  }

  /* ********************************************************** */
  // id (space id)*
  public static boolean command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "command")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ID);
    r = r && command_1(b, l + 1);
    exit_section_(b, m, COMMAND, r);
    return r;
  }

  // (space id)*
  private static boolean command_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "command_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!command_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "command_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // space id
  private static boolean command_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "command_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, SPACE, ID);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // id
  public static boolean config(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "config")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ID);
    exit_section_(b, m, CONFIG, r);
    return r;
  }

  /* ********************************************************** */
  // id
  public static boolean intask(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "intask")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ID);
    exit_section_(b, m, INTASK, r);
    return r;
  }

  /* ********************************************************** */
  // id
  public static boolean key(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "key")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ID);
    exit_section_(b, m, KEY, r);
    return r;
  }

  /* ********************************************************** */
  // id
  public static boolean projectId(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "projectId")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ID);
    exit_section_(b, m, PROJECT_ID, r);
    return r;
  }

  /* ********************************************************** */
  // thing | things
  static boolean sbtShellLine(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "sbtShellLine")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = thing(b, l + 1);
    if (!r) r = things(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // (OPEN_BRACE uri CLOSE_BRACE)? (projectId SLASH)? (config COLON)? (intask DOUBLE_COLON)? key
  public static boolean scopedKey(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "scopedKey")) return false;
    if (!nextTokenIs(b, "<scoped key>", OPEN_BRACE, ID)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, SCOPED_KEY, "<scoped key>");
    r = scopedKey_0(b, l + 1);
    r = r && scopedKey_1(b, l + 1);
    r = r && scopedKey_2(b, l + 1);
    r = r && scopedKey_3(b, l + 1);
    r = r && key(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (OPEN_BRACE uri CLOSE_BRACE)?
  private static boolean scopedKey_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "scopedKey_0")) return false;
    scopedKey_0_0(b, l + 1);
    return true;
  }

  // OPEN_BRACE uri CLOSE_BRACE
  private static boolean scopedKey_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "scopedKey_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, OPEN_BRACE);
    r = r && uri(b, l + 1);
    r = r && consumeToken(b, CLOSE_BRACE);
    exit_section_(b, m, null, r);
    return r;
  }

  // (projectId SLASH)?
  private static boolean scopedKey_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "scopedKey_1")) return false;
    scopedKey_1_0(b, l + 1);
    return true;
  }

  // projectId SLASH
  private static boolean scopedKey_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "scopedKey_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = projectId(b, l + 1);
    r = r && consumeToken(b, SLASH);
    exit_section_(b, m, null, r);
    return r;
  }

  // (config COLON)?
  private static boolean scopedKey_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "scopedKey_2")) return false;
    scopedKey_2_0(b, l + 1);
    return true;
  }

  // config COLON
  private static boolean scopedKey_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "scopedKey_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = config(b, l + 1);
    r = r && consumeToken(b, COLON);
    exit_section_(b, m, null, r);
    return r;
  }

  // (intask DOUBLE_COLON)?
  private static boolean scopedKey_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "scopedKey_3")) return false;
    scopedKey_3_0(b, l + 1);
    return true;
  }

  // intask DOUBLE_COLON
  private static boolean scopedKey_3_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "scopedKey_3_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = intask(b, l + 1);
    r = r && consumeToken(b, DOUBLE_COLON);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // scopedKey | command
  static boolean thing(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "thing")) return false;
    if (!nextTokenIs(b, "", OPEN_BRACE, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = scopedKey(b, l + 1);
    if (!r) r = command(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // (SEMICOLON thing)+
  static boolean things(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "things")) return false;
    if (!nextTokenIs(b, SEMICOLON)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = things_0(b, l + 1);
    int c = current_position_(b);
    while (r) {
      if (!things_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "things", c)) break;
      c = current_position_(b);
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // SEMICOLON thing
  private static boolean things_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "things_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SEMICOLON);
    r = r && thing(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // id
  public static boolean uri(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "uri")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ID);
    exit_section_(b, m, URI, r);
    return r;
  }

}
