package org.jetbrains.plugins.scala.annotator.intention;

import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author Alefas
 * @since 2/4/14
 */
public abstract class BaseListPopupStepAdapter<T> extends BaseListPopupStep<T> {
  public BaseListPopupStepAdapter(@Nullable String aTitle, T[] aValues) {
    super(aTitle, aValues);
  }

  public BaseListPopupStepAdapter(@Nullable String aTitle, List<? extends T> aValues) {
    super(aTitle, aValues);
  }

  public BaseListPopupStepAdapter(@Nullable String aTitle, T[] aValues, Icon[] aIcons) {
    super(aTitle, aValues, aIcons);
  }

  public BaseListPopupStepAdapter(@Nullable String aTitle, @NotNull List<? extends T> aValues, Icon aSameIcon) {
    super(aTitle, aValues, aSameIcon);
  }

  public BaseListPopupStepAdapter(@Nullable String aTitle, @NotNull List<? extends T> aValues, List<Icon> aIcons) {
    super(aTitle, aValues, aIcons);
  }

  public BaseListPopupStepAdapter() {
  }

  /**
   * @return PopupStep!!!
   */
  public abstract Object onChosenAdapter(T selectedValue, boolean finalChoice);

  @Override
  public PopupStep onChosen(T selectedValue, boolean finalChoice) {
    return (PopupStep) onChosenAdapter(selectedValue, finalChoice);
  }
}
