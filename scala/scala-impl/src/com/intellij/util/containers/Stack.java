package com.intellij.util.containers;

import java.util.EmptyStackException;
import java.util.Vector;

/**
 * A stack implementation that extends Vector.
 * This class is a modified version of java.util.Stack that handles empty stack cases gracefully.
 */
public class Stack<E> extends Vector<E> {
    /**
     * Creates an empty Stack.
     */
    public Stack() {
    }

    /**
     * Pushes an item onto the top of this stack.
     *
     * @param item the item to be pushed onto this stack.
     * @return the item argument.
     */
    public E push(E item) {
        addElement(item);
        return item;
    }

    /**
     * Removes the object at the top of this stack and returns that
     * object as the value of this function.
     * 
     * Modified to handle empty stack cases gracefully by returning null
     * instead of throwing an EmptyStackException.
     *
     * @return The object at the top of this stack or null if the stack is empty.
     */
    public E pop() {
        if (empty()) {
            // Return null instead of throwing an exception
            return null;
        }
        
        E obj = peek();
        removeElementAt(size() - 1);
        return obj;
    }

    /**
     * Looks at the object at the top of this stack without removing it
     * from the stack.
     *
     * @return the object at the top of this stack.
     * @throws EmptyStackException if this stack is empty.
     */
    public synchronized E peek() {
        int len = size();
        if (len == 0)
            throw new EmptyStackException();
        return elementAt(len - 1);
    }

    /**
     * Tests if this stack is empty.
     *
     * @return true if and only if this stack contains no items; false otherwise.
     */
    public boolean empty() {
        return size() == 0;
    }

    /**
     * Returns the 1-based position where an object is on this stack.
     * If the object o occurs as an item in this stack, this method
     * returns the distance from the top of the stack of the occurrence
     * nearest the top of the stack; the topmost item on the stack is
     * considered to be at distance 1.
     *
     * @param o the desired object.
     * @return the 1-based position from the top of the stack where the object is located;
     * the return value -1 indicates that the object is not on the stack.
     */
    public synchronized int search(Object o) {
        int i = lastIndexOf(o);
        if (i >= 0) {
            return size() - i;
        }
        return -1;
    }
}