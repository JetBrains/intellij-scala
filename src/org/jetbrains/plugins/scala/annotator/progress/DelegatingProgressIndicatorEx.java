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
public class DelegatingProgressIndicatorEx implements ProgressIndicatorEx {

  private ProgressIndicatorEx myDelegate;

  public DelegatingProgressIndicatorEx() {
    myDelegate = (ProgressIndicatorEx)ProgressManager.getInstance().getProgressIndicator();
  }

  public void addStateDelegate(@NotNull ProgressIndicatorEx delegate) {
    myDelegate.addStateDelegate(delegate);
  }

  public void initStateFrom(@NotNull ProgressIndicatorEx indicator) {
    myDelegate.initStateFrom(indicator);
  }

  @NotNull
  public Stack<String> getTextStack() {
    return myDelegate.getTextStack();
  }

  @NotNull
  public DoubleArrayList getFractionStack() {
    return myDelegate.getFractionStack();
  }

  @NotNull
  public Stack<String> getText2Stack() {
    return myDelegate.getText2Stack();
  }

  public int getNonCancelableCount() {
    return myDelegate.getNonCancelableCount();
  }

  public boolean isModalityEntered() {
    return myDelegate.isModalityEntered();
  }

  public void finish(@NotNull TaskInfo task) {
    myDelegate.finish(task);
  }

  public boolean isFinished(@NotNull TaskInfo task) {
    return myDelegate.isFinished(task);
  }

  public boolean wasStarted() {
    return myDelegate.wasStarted();
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
    return myDelegate.isRunning();
  }

  public void cancel() {
//    myDelegate.cancel();
  }

  public boolean isCanceled() {
    return myDelegate.isCanceled();
  }

  public void setText(String text) {
    myDelegate.setText(text);
  }

  public String getText() {
    return myDelegate.getText();
  }

  public void setText2(String text) {
    myDelegate.setText2(text);
  }

  public String getText2() {
    return myDelegate.getText2();
  }

  public double getFraction() {
    return myDelegate.getFraction();
  }

  public void setFraction(double fraction) {
    myDelegate.setFraction(fraction);
  }

  public void pushState() {
    myDelegate.pushState();
  }

  public void popState() {
    myDelegate.popState();
  }

  public void startNonCancelableSection() {
    myDelegate.startNonCancelableSection();
  }

  public void finishNonCancelableSection() {
    myDelegate.finishNonCancelableSection();
  }

  public boolean isModal() {
    return myDelegate.isModal();
  }

  public ModalityState getModalityState() {
    return myDelegate.getModalityState();
  }

  public void setModalityProgress(ProgressIndicator modalityProgress) {
    myDelegate.setModalityProgress(modalityProgress);
  }

  public boolean isIndeterminate() {
    return myDelegate.isIndeterminate();
  }

  public void setIndeterminate(boolean indeterminate) {
    myDelegate.setIndeterminate(indeterminate);
  }

  public void checkCanceled() throws ProcessCanceledException {
    myDelegate.checkCanceled();
  }
}