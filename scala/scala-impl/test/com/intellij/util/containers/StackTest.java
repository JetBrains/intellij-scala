package com.intellij.util.containers;

import junit.framework.TestCase;

public class StackTest extends TestCase {

    public void testPopEmptyStack() {
        Stack<String> stack = new Stack<>();
        assertNull("Pop on empty stack should return null", stack.pop());
    }

    public void testPushAndPop() {
        Stack<String> stack = new Stack<>();
        stack.push("test");
        assertEquals("test", stack.pop());
        assertNull("Pop on empty stack should return null", stack.pop());
    }

    public void testMultiplePushAndPop() {
        Stack<String> stack = new Stack<>();
        stack.push("first");
        stack.push("second");
        assertEquals("second", stack.pop());
        assertEquals("first", stack.pop());
        assertNull("Pop on empty stack should return null", stack.pop());
    }
}
