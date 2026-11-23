package com.termux.app;

import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;

import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.termux.shared.termux.terminal.TermuxTerminalViewClientBase;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public class TerminalImeReturnTest {
    private TerminalView view;
    private BaseInputConnection connection;
    private final StringBuilder sent = new StringBuilder();

    private boolean restartPending() {
        return ReflectionHelpers.getField(view, "mImeRestartPending");
    }

    @Before
    public void setUp() {
        view = new TerminalView(RuntimeEnvironment.getApplication(), null);
        view.mTermSession = new TerminalSession("/bin/sh", "/", new String[0], new String[0],
            null, new TermuxTerminalSessionClientBase());
        view.setTerminalViewClient(new TermuxTerminalViewClientBase() {
            @Override public boolean shouldEnableImeComposing() { return true; }
            @Override public boolean onCodePoint(int codePoint, boolean ctrl, TerminalSession session) {
                sent.appendCodePoint(ctrl ? codePoint & 31 : codePoint);
                return true;
            }
            // Consume keys after TerminalView's IME boundary handling; no native PTY is needed.
            @Override public boolean onKeyDown(int keyCode, KeyEvent event, TerminalSession session) {
                return true;
            }
        });
        TerminalOutput output = new TerminalOutput() {
            @Override public void write(byte[] data, int offset, int count) { }
            @Override public void titleChanged(String oldTitle, String newTitle) { }
            @Override public void onCopyTextToClipboard(String text) { }
            @Override public void onPasteTextFromClipboard() { }
            @Override public void onBell() { }
            @Override public void onColorsChanged() { }
        };
        view.mEmulator = new TerminalEmulator(output, 80, 24, 8, 16, null,
            new TermuxTerminalSessionClientBase());
        connection = (BaseInputConnection) view.onCreateInputConnection(new EditorInfo());
    }

    @Test
    public void returnAfterCommitStillRequestsKeyboardReset() {
        connection.setComposingText("ls", 1);
        connection.commitText("ls", 1);
        assertEquals(false, restartPending());
        view.onKeyDown(KeyEvent.KEYCODE_ENTER,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        assertEquals("ls", sent.toString());
        assertEquals(0, connection.getEditable().length());
        assertEquals(true, restartPending());
    }

    @Test
    public void returnFinalizesPendingCompositionOnlyOnce() {
        connection.setComposingText("ls", 1);
        view.onKeyDown(KeyEvent.KEYCODE_ENTER,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        assertEquals("ls", sent.toString());
        assertEquals(0, connection.getEditable().length());
        assertEquals(true, restartPending());
    }

    @Test
    public void newlineTextAlsoRequestsKeyboardReset() {
        connection.commitText("ls", 1);
        connection.commitText("\n", 1);
        assertEquals("ls\r", sent.toString());
        assertEquals(true, restartPending());
    }

    @Test
    public void ordinaryTextAndShiftPreserveComposition() {
        connection.commitText("123", 1);
        connection.setComposingText("해", 1);
        view.onKeyDown(KeyEvent.KEYCODE_SHIFT_LEFT,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT));
        assertEquals("123", sent.toString());
        assertEquals("해", connection.getEditable().toString());
        assertEquals(false, restartPending());
    }
}
