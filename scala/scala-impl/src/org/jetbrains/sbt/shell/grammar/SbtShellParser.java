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
    else if (t == PARAMS) {
      r = params(b, 0);
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
  // (COLON | DOUBLE_COLON | SLASH | ID | URISTRING | OPEN_BRACE | CLOSE_BRACE | ANYCHAR)+
  static boolean anything(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "anything")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = anything_0(b, l + 1);
    int c = current_position_(b);
    while (r) {
      if (!anything_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "anything", c)) break;
      c = current_position_(b);
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // COLON | DOUBLE_COLON | SLASH | ID | URISTRING | OPEN_BRACE | CLOSE_BRACE | ANYCHAR
  private static boolean anything_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "anything_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COLON);
    if (!r) r = consumeToken(b, DOUBLE_COLON);
    if (!r) r = consumeToken(b, SLASH);
    if (!r) r = consumeToken(b, ID);
    if (!r) r = consumeToken(b, URISTRING);
    if (!r) r = consumeToken(b, OPEN_BRACE);
    if (!r) r = consumeToken(b, CLOSE_BRACE);
    if (!r) r = consumeToken(b, ANYCHAR);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // (SEMICOLON commandOrKey)+
  static boolean chainedCommands(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "chainedCommands")) return false;
    if (!nextTokenIs(b, SEMICOLON)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = chainedCommands_0(b, l + 1);
    int c = current_position_(b);
    while (r) {
      if (!chainedCommands_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "chainedCommands", c)) break;
      c = current_position_(b);
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // SEMICOLON commandOrKey
  private static boolean chainedCommands_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "chainedCommands_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SEMICOLON);
    r = r && commandOrKey(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // id
  public static boolean command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "command")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = id(b, l + 1);
    exit_section_(b, m, COMMAND, r);
    return r;
  }

  /* ********************************************************** */
  // (scopedKey | command) (params)*
  static boolean commandOrKey(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commandOrKey")) return false;
    if (!nextTokenIs(b, "", ID, URISTRING)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = commandOrKey_0(b, l + 1);
    r = r && commandOrKey_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // scopedKey | command
  private static boolean commandOrKey_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commandOrKey_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = scopedKey(b, l + 1);
    if (!r) r = command(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (params)*
  private static boolean commandOrKey_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commandOrKey_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!commandOrKey_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "commandOrKey_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // (params)
  private static boolean commandOrKey_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commandOrKey_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = params(b, l + 1);
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
    r = id(b, l + 1);
    exit_section_(b, m, CONFIG, r);
    return r;
  }

  /* ********************************************************** */
  // ID
  static boolean id(PsiBuilder b, int l) {
    return consumeToken(b, ID);
  }

  /* ********************************************************** */
  // id
  public static boolean intask(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "intask")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = id(b, l + 1);
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
    r = id(b, l + 1);
    exit_section_(b, m, KEY, r);
    return r;
  }

  /* ********************************************************** */
  // anything
  public static boolean params(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "params")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PARAMS, "<params>");
    r = anything(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // id
  public static boolean projectId(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "projectId")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = id(b, l + 1);
    exit_section_(b, m, PROJECT_ID, r);
    return r;
  }

  /* ********************************************************** */
  // commandOrKey | chainedCommands?
  static boolean sbtShellLine(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "sbtShellLine")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = commandOrKey(b, l + 1);
    if (!r) r = sbtShellLine_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // chainedCommands?
  private static boolean sbtShellLine_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "sbtShellLine_1")) return false;
    chainedCommands(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // uri? (projectId SLASH)? (config COLON)? (intask DOUBLE_COLON)? key
  public static boolean scopedKey(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "scopedKey")) return false;
    if (!nextTokenIs(b, "<scoped key>", ID, URISTRING)) return false;
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

  // uri?
  private static boolean scopedKey_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "scopedKey_0")) return false;
    uri(b, l + 1);
    return true;
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
  // URISTRING
  public static boolean uri(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "uri")) return false;
    if (!nextTokenIs(b, URISTRING)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, URISTRING);
    exit_section_(b, m, URI, r);
    return r;
  }

}
