package org.example;

public class MyJavaClassInSharedSources {
    static {
        {
            new library_package.LibraryClass().libraryMethod();
        }
    }
}
