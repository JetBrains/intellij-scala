/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.scala.annotator.progress;

import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.util.containers.Stack;
import com.intellij.util.containers.DoubleArrayList;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
// todo The null checks in this class were added because when ProgressManager.getInstance().getProgressIndicator()
//      returned null when running the HighlightingPerformanceTest. Is there a better solution?
public class DelegatingProgressIndicatorEx implements ProgressIndicatorEx {

  private ProgressIndicatorEx myDelegate;

  public DelegatingProgressIndicatorEx() {
    myDelegate = (ProgressIndicatorEx) ProgressManager.getInstance().getProgressIndicator();
  }

  public void addStateDelegate(@NotNull ProgressIndicatorEx delegate) {
    if (myDelegate != null) {
      myDelegate.addStateDelegate(delegate);
    }
  }

  public void initStateFrom(@NotNull ProgressIndicatorEx indicator) {
    if (myDelegate != null) {
      myDelegate.initStateFrom(indicator);
    }
  }

  @NotNull
  public Stack<String> getTextStack() {
    if (myDelegate != null) {
      return myDelegate.getTextStack();
    } else {
      return new Stack<String>();
    }
  }

  @NotNull
  public DoubleArrayList getFractionStack() {
    if (myDelegate != null) {
      return myDelegate.getFractionStack();
    } else {
      return new DoubleArrayList();
    }
  }

  @NotNull
  public Stack<String> getText2Stack() {
    if (myDelegate != null) {
      return myDelegate.getText2Stack();
    } else {
      return new Stack<String>();
    }
  }

  public int getNonCancelableCount() {
    if (myDelegate != null) {
      return myDelegate.getNonCancelableCount();
    } else {
      return 0;
    }
  }

  public boolean isModalityEntered() {
    if (myDelegate != null) {
      return myDelegate.isModalityEntered();
    } else {
      return false;
    }
  }

  public void finish(@NotNull TaskInfo task) {
    if (myDelegate != null) {
      myDelegate.finish(task);
    }
  }

  public boolean isFinished(@NotNull TaskInfo task) {
    if (myDelegate != null) {
      return myDelegate.isFinished(task);
    } else {
      return true;
    }
  }

  public boolean wasStarted() {
    if (myDelegate != null) {
      return myDelegate.wasStarted();
    } else {
      return true;
    }
  }

  public void processFinish() {
//    myDelegate.processFinish();
  }

  public void start() {
//    myDelegate.start();
  }

  public void stop() {
//    myDelegate.stop();
  }

  public boolean isRunning() {
    if (myDelegate != null) {
      return myDelegate.isRunning();
    } else {
      return false;
    }
  }

  public void cancel() {
//    myDelegate.cancel();
  }

  public boolean isCanceled() {
    if (myDelegate != null) {
      return myDelegate.isCanceled();
    } else {
      return false;
    }
  }

  public void setText(String text) {
    if (myDelegate != null) {
      myDelegate.setText(text);
    }
  }

  public String getText() {
    if (myDelegate != null) {
      return myDelegate.getText();
    } else {
      return "";
    }
  }

  public void setText2(String text) {
    if (myDelegate != null) {
      myDelegate.setText2(text);
    }
  }

  public String getText2() {
    if (myDelegate != null) {
      return myDelegate.getText2();
    } else {
      return "";
    }
  }

  public double getFraction() {
    if (myDelegate != null) {
      return myDelegate.getFraction();
    } else {
      return 0;
    }
  }

  public void setFraction(double fraction) {
    if (myDelegate != null) {
      myDelegate.setFraction(fraction);
    }
  }

  public void pushState() {
    if (myDelegate != null) {
      myDelegate.pushState();
    }
  }

  public void popState() {
    if (myDelegate != null) {
      myDelegate.popState();
    }
  }

  public void startNonCancelableSection() {
    if (myDelegate != null) {
      myDelegate.startNonCancelableSection();
    }
  }

  public void finishNonCancelableSection() {
    if (myDelegate != null) {
      myDelegate.finishNonCancelableSection();
    }
  }

  public boolean isModal() {
    if (myDelegate != null) {
      return myDelegate.isModal();
    } else {
      return false;
    }
  }

  public ModalityState getModalityState() {
    if (myDelegate != null) {
      return myDelegate.getModalityState();
    } else {
      return ModalityState.defaultModalityState();
    }
  }

  public void setModalityProgress(ProgressIndicator modalityProgress) {
    if (myDelegate != null) {
      myDelegate.setModalityProgress(modalityProgress);
    }
  }

  public boolean isIndeterminate() {
    if (myDelegate != null) {
      return myDelegate.isIndeterminate();
    } else {
      return true;
    }
  }

  public void setIndeterminate(boolean indeterminate) {
    if (myDelegate != null) {
      myDelegate.setIndeterminate(indeterminate);
    }
  }

  public void checkCanceled() throws ProcessCanceledException {
    if (myDelegate != null) {
      myDelegate.checkCanceled();
    }
  }
}