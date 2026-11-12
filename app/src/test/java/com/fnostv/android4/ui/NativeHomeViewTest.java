package com.fnostv.android4.ui;

public final class NativeHomeViewTest {
    public static void main(String[] args) {
        userMenuOnlyKeepsLogout();
    }

    private static void userMenuOnlyKeepsLogout() {
        assertEquals("logout", NativeHomeView.ACTION_LOGOUT);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }
}
