package org.jetbrains.plugins.scala.testingSupport.scalaTest.treeBuilder;

import org.jetbrains.plugins.scala.testingSupport.MyJavaConverters;
import org.scalatest.events.Ordinal;
import org.scalatest.events.RunStarting;
import org.scalatest.events.SuiteCompleted;
import org.scalatest.events.SuiteStarting;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil.escapeString;
import static org.jetbrains.plugins.scala.testingSupport.scalaTest.TeamcityReporter.reportMessage;

public class ParallelTreeBuilder implements TreeBuilder {

  private static List<Integer> getOrdinalList(Ordinal ordinal) {
    List<?> newOrdinalList = MyJavaConverters.toJava(ordinal.toList());
    ArrayList<Integer> result = new ArrayList<>(newOrdinalList.size());
    for (Object o : newOrdinalList) {
      result.add((Integer) o);
    }
    return result;
  }

  private final AtomicInteger idGenerator = new AtomicInteger(0);
  private final AtomicBoolean isParallelSuitesMode = new AtomicBoolean(false);
  private volatile boolean suiteModeDetermined = false;

  private final ConcurrentHashMap<SuiteTree, Stack<String>> waitingScopeMessages = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<SuiteTree, Stack<Integer>> ids = new ConcurrentHashMap<>();

  private int generateId() {
    return idGenerator.getAndIncrement();
  }

  private final Object outLock = new Object();
  private SuiteTree suiteRoot;
  private final ConcurrentHashMap<SuiteId, SuiteTree> suiteById = new ConcurrentHashMap<>();
  private final Stack<SuiteTree> suiteStack = new Stack<>();

  private static class SuiteId {
    private final String suiteId;
    private final List<Integer> contextOrdinal;
    public SuiteId(String suiteId, List<Integer> contextOrdinal) {
      this.suiteId = suiteId;
      this.contextOrdinal = contextOrdinal;
    }
    public SuiteId(String suiteId, Ordinal ordinal) {
      this.suiteId = suiteId;
      List<Integer> fullOrdinal = getOrdinalList(ordinal);
      this.contextOrdinal = fullOrdinal.subList(0, fullOrdinal.size() - 1);

    }
    public SuiteId(SuiteStarting suiteStarting) {
      this(suiteStarting.suiteId(), suiteStarting.ordinal());
    }
    public SuiteId(SuiteCompleted suiteCompleted) {
      this(suiteCompleted.suiteId(), suiteCompleted.ordinal());
    }
    @Override
    public boolean equals(Object o) {
      if (o instanceof SuiteId) {
        SuiteId otherId = (SuiteId) o;
        return otherId.suiteId.equals(suiteId) && otherId.contextOrdinal.equals(contextOrdinal);
      } else return false;
    }
    @Override
    public int hashCode() {
      return suiteId.hashCode() + 41 * contextOrdinal.hashCode();
    }
  }

  private class SuiteTree {
    private SuiteTree parent = null;
    public final int id;
    private final int ordinalTail;
    private final List<Integer> ordinalElements;
    private final Map<Integer, SuiteTree> children = new HashMap<>();

    public SuiteTree getParent() {
      return parent;
    }

    private int getLastOrdinalElement() {
      return ordinalElements.get(ordinalElements.size() - 1);
    }

    public SuiteTree(Ordinal ordinal, int id) {
      List<Integer> ordinals = getOrdinalList(ordinal);
      this.ordinalElements = ordinals.subList(0, ordinals.size() - 1);
      this.ordinalTail = ordinals.get(ordinals.size() - 1);
      this.id = id;
    }

    public int placeSuite(SuiteTree suite) {
      //suites are organized in a tree-like structure
      if (!suiteModeDetermined) {
        isParallelSuitesMode.set(suite.ordinalElements.size() != ordinalElements.size());
        suiteModeDetermined = true;
      }
      SuiteTree parent;
      if (isParallelSuitesMode.get()) {
        parent = findParent(suite.ordinalElements, 0);
        parent.children.put(suite.getLastOrdinalElement(), suite);
      } else {
        //use stack to build suites tree
        synchronized (ParallelTreeBuilder.this) {
          if (suiteStack.isEmpty()) {
            suiteStack.push(suiteRoot);
          }
          parent = suiteStack.peek();
        }
        this.children.put(suite.ordinalTail, suite);
      }
      suite.parent = parent;
      return parent.id;
    }

    private SuiteTree findParent(List<Integer> ordinals, int level) {

      //use ordinals.size() - 2 because we don't want to consider the last element of ordinals - it is ID in current suite
      if (level == ordinals.size() - 2) {
        return this;
      }
      int ordinalElement = ordinals.get(level + 1);
      SuiteTree nextChild = children.get(ordinalElement);
      assert (nextChild != null);
      return nextChild.findParent(ordinals, level + 1);
    }

    @Override
    public int hashCode() {
      return id;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof SuiteTree && ((SuiteTree) o).id == id;
    }

    public void openScope(String message, boolean isTestStarted) {
      Stack<Integer> myIds = ids.get(this);
      Stack<String> myWaitingMessages = waitingScopeMessages.get(this);
      int parentNode = myIds.peek();
      int id = generateId();
      myIds.push(id);
      myWaitingMessages.push("##teamcity[" + message + " nodeId='" + id + "' parentNodeId='" + parentNode + "']");
      if (isTestStarted) {
        for (String openMessage : myWaitingMessages) {
          synchronized (outLock) {
            reportMessage(openMessage);
          }
        }
        myWaitingMessages.clear();
      }
    }

    public void closePendingScope(String scopePendingMessage) {
      Stack<Integer> myIds = ids.get(this);
      Stack<String> myWaitingMessages = waitingScopeMessages.get(this);
      if (myWaitingMessages.isEmpty()) {
        //print three messages from ScopePending event processing
        reportMessage("##teamcity[testIgnored name='(Scope Pending)' message='" + escapeString("Scope Pending") + "' nodeId='" + myIds.peek() + "']");
        reportMessage("##teamcity[testIgnored name='" + escapeString(scopePendingMessage) + "' message='" + escapeString("Scope Pending") + "' nodeId='" + myIds.peek() + "']");
        reportMessage("##teamcity[testSuiteFinished name='" + escapeString(scopePendingMessage) + "' nodeId='" + myIds.peek() + "']");
      } else {
        myWaitingMessages.pop();
      }
      myIds.pop();
    }

    public void closeScope(String message) {
      Stack<Integer> myIds = ids.get(this);
      Stack<String> myWaitingMessages = waitingScopeMessages.get(this);
      if (myWaitingMessages.isEmpty()) {
        //there are no open empty scopes, so scope currently being closed must be not empty, print the actual message
        reportMessage("##teamcity[" + message + "nodeId='" + myIds.peek() + "']");
      } else {
        myWaitingMessages.pop();
      }
      myIds.pop();
    }
  }

  public SuiteTree getSuite(String suiteId, Ordinal ordinal) {
    SuiteTree res = null;
    List<Integer> ordinalElements = getOrdinalList(ordinal);
    while (res == null) {
      res =  suiteById.get(new SuiteId(suiteId, ordinalElements));
      //if there was no suite with such (id, ordinal) pair, go higher in ordinal hierarchy
      ordinalElements = ordinalElements.subList(0, ordinalElements.size() - 1);
    }
    return res;
  }

  @Override
  public void closePendingScope(String scopePendingMessage, Ordinal ordinal, String suiteId) {
    SuiteTree suite = getSuite(suiteId, ordinal);
    suite.closePendingScope(scopePendingMessage);
  }

  @Override
  public void openScope(String message, Ordinal ordinal, String suiteId, boolean isTestStarted) {
    SuiteTree suite = getSuite(suiteId, ordinal);
    suite.openScope(message, isTestStarted);
  }

  @Override
  public void closeScope(String message, Ordinal ordinal, String suiteId, boolean isTestFinished) {
    SuiteTree suite = getSuite(suiteId, ordinal);
    suite.closeScope(message);
  }

  @Override
  public void openSuite(String message, SuiteStarting suiteStarting) {
    int id = generateId();
    SuiteTree nextSuite = new SuiteTree(suiteStarting.ordinal(), id);
    suiteById.put(new SuiteId(suiteStarting), nextSuite);
    int parentId = suiteRoot.placeSuite(nextSuite);
    if (!isParallelSuitesMode.get()) {
      synchronized (this) {
        suiteStack.push(nextSuite);
      }
    }
    waitingScopeMessages.put(nextSuite, new Stack<>());
    Stack<Integer> myIds = new Stack<>();
    myIds.push(id);
    ids.put(nextSuite, myIds);
    //suite place found, report that it has started
    synchronized (outLock) {
      reportMessage("##teamcity[" + message + " nodeId='" + id + "' parentNodeId='" + parentId + "']");
    }
  }


  @Override
  public void closeSuite(String message, SuiteCompleted suiteCompleted) {
    int parentId = suiteById.get(new SuiteId(suiteCompleted)).id;
    if (!isParallelSuitesMode.get()) {
      synchronized (this) {
        suiteStack.pop();
      }
    }
    synchronized (outLock) {
      reportMessage("##teamcity[" + message + "nodeId='" + parentId + "']");
    }
  }

  @Override
  public void initRun(RunStarting runStarting) {
    int id = generateId();
    suiteRoot = new SuiteTree(runStarting.ordinal(), id);
    waitingScopeMessages.put(suiteRoot, new Stack<>());
  }
}
