// SPDX-License-Identifier: GPL-3.0-or-later
package io.zer0.muse.cyrene.verifier;

import android.app.Instrumentation;
import android.os.Bundle;

/** Platform-only verifier: independent of the target APK's optimized Kotlin ABI. */
public final class MessageBubbleArtVerifier extends Instrumentation {
    private String className;
    private boolean inventory;
    private boolean roomUi;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        className = arguments == null ? null : arguments.getString("className");
        inventory = arguments != null && "true".equals(arguments.getString("inventory"));
        roomUi = arguments != null && "true".equals(arguments.getString("roomUi"));
        if (className == null) className = "io.zer0.muse.ui.MessageBubbleKt";
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        result.putString("verifiedClass", className);
        try {
            if (roomUi) {
                android.content.Intent launch = getTargetContext().getPackageManager().getLaunchIntentForPackage(getTargetContext().getPackageName());
                if (launch == null) throw new AssertionError("No launcher activity");
                startActivitySync(launch);
                clickText("群聊");
                findText("新建群聊");
                android.graphics.Bitmap screenshot = getUiAutomation().takeScreenshot();
                java.io.File proof = new java.io.File(getContext().getExternalFilesDir(null), "chatroom-device.png");
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(proof)) {
                    if (screenshot == null || !screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)) throw new AssertionError("Screenshot failed");
                }
                clickText("新建群聊");
                findText("请选择 2–8 个角色", false); // New guidance contains the same member range.
                android.view.accessibility.AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
                if (root == null || root.findAccessibilityNodeInfosByText("2–8").isEmpty()) throw new AssertionError("Room creation guidance absent");
                clickText("取消");
                result.putString("roomUi", "PASS"); result.putString("screenshot", proof.getPath()); finish(-1, result); return;
            }
            if (inventory) {
                // Counts only: do not read, export or alter private conversations or credentials.
                String[] tables = {"assistants", "sessions", "messages", "group_chats", "group_chat_messages", "group_chat_memories", "facts"};
                long[] counts = new long[tables.length];
                for (String filename : getTargetContext().databaseList()) {
                    if (filename.endsWith("-wal") || filename.endsWith("-shm") || filename.endsWith("-journal")) continue;
                    java.io.File file = getTargetContext().getDatabasePath(filename);
                    if (!file.isFile()) continue;
                    try (android.database.sqlite.SQLiteDatabase database = android.database.sqlite.SQLiteDatabase.openDatabase(file.getPath(), null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)) {
                        for (int i = 0; i < tables.length; i++) {
                            try (android.database.Cursor exists = database.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", new String[]{tables[i]})) {
                                if (!exists.moveToFirst()) continue;
                            }
                            try (android.database.Cursor count = database.rawQuery("SELECT COUNT(*) FROM " + tables[i], null)) {
                                if (count.moveToFirst()) counts[i] += count.getLong(0);
                            }
                        }
                    }
                }
                for (int i = 0; i < tables.length; i++) result.putLong(tables[i], counts[i]);
                result.putString("inventory", "PASS"); finish(-1, result); return;
            }
            Class<?> facade = Class.forName(className, true, getTargetContext().getClassLoader());
            int count = facade.getDeclaredMethods().length;
            if (count == 0) throw new AssertionError("No renderer methods in target class");
            result.putString("verification", "PASS");
            result.putInt("methodCount", count);
            finish(-1, result);
        } catch (Throwable failure) {
            result.putString("verification", "FAIL");
            result.putString("error", failure.toString());
            finish(0, result);
        }
    }
    private android.view.accessibility.AccessibilityNodeInfo findText(String text) throws Exception { return findText(text, true); }
    private android.view.accessibility.AccessibilityNodeInfo findText(String text, boolean required) throws Exception {
        for (int attempt = 0; attempt < (required ? 60 : 3); attempt++) {
            android.view.accessibility.AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) for (android.view.accessibility.AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
                if (text.contentEquals(node.getText() == null ? "" : node.getText())) return node;
            }
            Thread.sleep(150);
        }
        if (required) throw new AssertionError("Missing UI label: " + text);
        return null;
    }
    private void clickText(String text) throws Exception {
        android.view.accessibility.AccessibilityNodeInfo node = findText(text);
        for (int parent = 0; parent < 8 && node != null; parent++, node = node.getParent()) {
            if (node.isClickable()) {
                if (!node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) throw new AssertionError("Click failed: " + text);
                getUiAutomation().waitForIdle(300, 5000); return;
            }
        }
        throw new AssertionError("No clickable ancestor: " + text);
    }
}
