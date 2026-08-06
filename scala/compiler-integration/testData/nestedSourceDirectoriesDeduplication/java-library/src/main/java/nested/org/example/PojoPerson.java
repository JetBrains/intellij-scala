package org.example;

public final class PojoPerson {
    private final String name;
    private final int age;

    public PojoPerson(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }
}
