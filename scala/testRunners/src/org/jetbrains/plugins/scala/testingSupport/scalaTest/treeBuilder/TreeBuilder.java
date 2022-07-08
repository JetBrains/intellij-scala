package org.jetbrains.plugins.scala.testingSupport.scalaTest.treeBuilder;

import org.scalatest.events.Ordinal;
import org.scalatest.events.RunStarting;
import org.scalatest.events.SuiteCompleted;
import org.scalatest.events.SuiteStarting;

import java.util.Stack;

import static org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil.escapeString;

public interface TreeBuilder {
  void openScope(String message, Ordinal ordinal, String suiteId, boolean isTestStarted);
  void openSuite(String message, SuiteStarting suiteStarting);
  void closePendingScope(String scopePendingMessage, Ordinal ordinal, String suiteId);
  void closeScope(String message, Ordinal ordinal, String suiteId, boolean isTestFinished);
  void closeSuite(String message, SuiteCompleted suiteCompleted);
  void initRun(RunStarting runStarting);
}
