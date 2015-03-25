package org.jetbrains.plugins.scala.worksheet.ui;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.editor.impl.FoldingModelImpl;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Dmitry.Naydanov
 * Date: 09.04.14.
 */
public class WorksheetFoldRegionDelegate implements FoldRegion {
  private final static int PLACEHOLDER_LIMIT = 75;

  private final FoldRegion delegate;
  private final WorksheetFoldGroup worksheetGroup;

  public WorksheetFoldRegionDelegate(Editor editor, int start, int end, int leftStart,
                                     int spaces, WorksheetFoldGroup worksheetGroup, int leftSideLength) {
    String placeholder = editor.getDocument().getText(new TextRange(start, start + Math.min(end - start, PLACEHOLDER_LIMIT)));

    delegate = ((FoldingModelImpl) editor.getFoldingModel()).createFoldRegion(start, end, placeholder, null, false);
    delegate.setExpanded(false);  //No, it can't >___<
    this.worksheetGroup = worksheetGroup;
    worksheetGroup.addRegion(this, leftStart, spaces, leftSideLength);
  }


  public FoldRegion getDelegate() {
    return delegate;
  }

  public WorksheetFoldGroup getWorksheetGroup() {
    return worksheetGroup;
  }

  @Override
  public boolean isExpanded() {
    return delegate.isExpanded();
  }

  @Override
  public void setExpanded(boolean expanded) {
    boolean ok = expanded? worksheetGroup.onExpand(this) : worksheetGroup.onCollapse(this);
    if (ok) delegate.setExpanded(expanded);

    CodeFoldingManager.getInstance(getEditor().getProject()).updateFoldRegions(getEditor());
  }

  @Override
  @NotNull
  public String getPlaceholderText() {
    return delegate.getPlaceholderText();
  }

  @Override
  public Editor getEditor() {
    return delegate.getEditor();
  }

  @Override
  @Nullable
  public FoldingGroup getGroup() {
    return delegate.getGroup();
  }

  @Override
  public boolean shouldNeverExpand() {
    return delegate.shouldNeverExpand();
  }

  @Override
  @NotNull
  public Document getDocument() {
    return delegate.getDocument();
  }

  @Override
  public int getStartOffset() {
    return delegate.getStartOffset();
  }

  @Override
  public int getEndOffset() {
    return delegate.getEndOffset();
  }

  @Override
  public boolean isValid() {
    return delegate.isValid();
  }

  @Override
  public void setGreedyToLeft(boolean greedy) {
    delegate.setGreedyToLeft(greedy);
  }

  @Override
  public void setGreedyToRight(boolean greedy) {
    delegate.setGreedyToRight(greedy);
  }

  @Override
  public boolean isGreedyToRight() {
    return delegate.isGreedyToRight();
  }

  @Override
  public boolean isGreedyToLeft() {
    return delegate.isGreedyToLeft();
  }

  @Override
  public void dispose() {
    delegate.dispose();
  }

  @Override
  @Nullable
  public <T> T getUserData(@NotNull Key<T> key) {
    return delegate.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    delegate.putUserData(key, value);
  }
}
