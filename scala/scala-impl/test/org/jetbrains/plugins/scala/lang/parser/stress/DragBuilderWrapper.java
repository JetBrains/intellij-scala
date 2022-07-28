package org.jetbrains.plugins.scala.lang.parser.stress;

import com.intellij.lang.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DragBuilderWrapper implements PsiBuilder {
  final Project myProject;
  final PsiBuilder myBuilder;
  final DragStorage myStorage;

  public DragBuilderWrapper(Project project, PsiBuilder builder) {
    myProject = project;
    myBuilder = builder;
    myStorage = new DragStorage();
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public void advanceLexer() {
    String text = myBuilder.getTokenText();
    if (!myBuilder.eof() && text != null) {
      int start = myBuilder.getCurrentOffset();
      int end = start + text.length();
      TextRange range = new TextRange(start, end);
      myStorage.registerRevision(range);
    }
    myBuilder.advanceLexer();
  }

  public Pair<TextRange, Integer>[] getDragInfo(){
    return myStorage.getRangeInfo();
  }

  /**
   * *******************************************<br>
   * Wrap other PsiBuilder's methods<br>
   * ********************************************
   */

  @NotNull
  @Override
  public CharSequence getOriginalText() {
    return myBuilder.getOriginalText();
  }

/*
  public void setTokenTypeRemapper(ITokenTypeRemapper remapper)  {
    myBuilder.setTokenTypeRemapper(remapper);
  }
*/

  @Override
  public IElementType getTokenType() {
    return myBuilder.getTokenType();
  }

  @Override
  public String getTokenText() {
    return myBuilder.getTokenText();
  }

  @Override
  public int getCurrentOffset() {
    return myBuilder.getCurrentOffset();
  }

  @Override
  public void setTokenTypeRemapper(ITokenTypeRemapper remapper) {
    myBuilder.setTokenTypeRemapper(remapper);
  }

  @Override
  public void remapCurrentToken(IElementType type) {
    myBuilder.remapCurrentToken(type);
  }

  @Override
  public void setWhitespaceSkippedCallback(WhitespaceSkippedCallback callback) {
   myBuilder.setWhitespaceSkippedCallback(callback);
  }

  @Override
  public IElementType lookAhead(int steps) {
    return myBuilder.lookAhead(steps);
  }

  @Override
  public IElementType rawLookup(int steps) {
    return myBuilder.rawLookup(steps);
  }

  @Override
  public int rawTokenTypeStart(int steps) {
    return myBuilder.rawTokenTypeStart(steps);
  }

  @Override
  public int rawTokenIndex() {
    return myBuilder.rawTokenIndex();
  }

  @Override
  @NotNull
  public Marker mark() {
    return myBuilder.mark();
  }

  @Override
  public void error(@NotNull String messageText) {
    myBuilder.error(messageText);
  }

  @Override
  public boolean eof() {
    return myBuilder.eof();
  }

  @Override
  @NotNull
  public ASTNode getTreeBuilt() {
    return myBuilder.getTreeBuilt();
  }

  @Override
  @NotNull
  public FlyweightCapableTreeStructure<LighterASTNode> getLightTree() {
    return myBuilder.getLightTree();
  }

  @Override
  public void setDebugMode(boolean dbgMode) {
    myBuilder.setDebugMode(dbgMode);
  }

  @Override
  public void enforceCommentTokens(@NotNull TokenSet tokens) {
    myBuilder.enforceCommentTokens(tokens);
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return myBuilder.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, T value) {
    myBuilder.putUserData(key, value);
  }

  @Override
  public LighterASTNode getLatestDoneMarker() {
    return myBuilder.getLatestDoneMarker();
  }

  @SuppressWarnings("deprecation")
  @Override
  public <T> T getUserDataUnprotected(@NotNull Key<T> key) {
    return myBuilder.getUserDataUnprotected(key);
  }

  @SuppressWarnings("deprecation")
  @Override
  public <T> void putUserDataUnprotected(@NotNull Key<T> key, @Nullable T value) {
    myBuilder.putUserDataUnprotected(key, value);
  }
}
