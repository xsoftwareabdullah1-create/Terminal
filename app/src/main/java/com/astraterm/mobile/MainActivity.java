package com.astraterm.mobile;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    static {
        System.loadLibrary("astraterm");
    }

    private static native int nativeStartShell(String home, String temp);

    private TerminalView terminal;
    private TextView status;
    private ParcelFileDescriptor pty;
    private ParcelFileDescriptor readPty;
    private ParcelFileDescriptor writePty;
    private FileInputStream shellOutput;
    private FileOutputStream shellInput;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean ctrlPending = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        terminal = findViewById(R.id.terminal);
        status = findViewById(R.id.status);
        EditText command = findViewById(R.id.commandInput);
        Button send = findViewById(R.id.send);

        send.setOnClickListener(v -> submit(command));
        command.setOnEditorActionListener((view, action, event) -> {
            boolean enter = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (action == EditorInfo.IME_ACTION_DONE || enter) {
                submit(command);
                return true;
            }
            return false;
        });

        findViewById(R.id.keyCtrl).setOnClickListener(v -> {
            ctrlPending = !ctrlPending;
            status.setText(ctrlPending ? "CTRL READY" : (running.get() ? "CONNECTED" : "SHELL EXITED"));
        });
        findViewById(R.id.keyEsc).setOnClickListener(v -> write("\u001b"));
        findViewById(R.id.keyTab).setOnClickListener(v -> write("\t"));
        findViewById(R.id.keyUp).setOnClickListener(v -> write("\u001b[A"));
        findViewById(R.id.keyDown).setOnClickListener(v -> write("\u001b[B"));
        findViewById(R.id.keyLeft).setOnClickListener(v -> write("\u001b[D"));
        findViewById(R.id.keyRight).setOnClickListener(v -> write("\u001b[C"));

        startShell();
    }

    private void startShell() {
        File home = new File(getFilesDir(), "home");
        File temp = new File(getCacheDir(), "tmp");
        if ((!home.isDirectory() && !home.mkdirs()) || (!temp.isDirectory() && !temp.mkdirs())) {
            showStartupError("Cannot create app-private home/temp directory.");
            return;
        }

        int fd = nativeStartShell(home.getAbsolutePath(), temp.getAbsolutePath());
        if (fd < 0) {
            showStartupError("PTY startup failed (errno " + (-fd) + ").");
            return;
        }

        try {
            pty = ParcelFileDescriptor.adoptFd(fd);
            readPty = ParcelFileDescriptor.dup(pty.getFileDescriptor());
            writePty = ParcelFileDescriptor.dup(pty.getFileDescriptor());
            shellOutput = new FileInputStream(readPty.getFileDescriptor());
            shellInput = new FileOutputStream(writePty.getFileDescriptor());
            running.set(true);
            status.setText("CONNECTED");

            Thread reader = new Thread(() -> {
                byte[] buffer = new byte[4096];
                try {
                    int count;
                    while (running.get() && shellOutput != null
                            && (count = shellOutput.read(buffer)) != -1) {
                        String output = new String(buffer, 0, count, StandardCharsets.UTF_8);
                        runOnUiThread(() -> {
                            if (terminal != null) terminal.appendOutput(output);
                        });
                    }
                } catch (IOException e) {
                    if (running.get()) {
                        runOnUiThread(() -> terminal.appendOutput("\n[PTY read error] " + e.getMessage() + "\n"));
                    }
                } finally {
                    running.set(false);
                    runOnUiThread(() -> {
                        if (status != null) status.setText("SHELL EXITED");
                    });
                }
            }, "AstraTerm-pty-reader");
            reader.setDaemon(true);
            reader.start();
        } catch (IOException | RuntimeException e) {
            running.set(false);
            closePty();
            showStartupError("Unable to connect PTY: " + e.getMessage());
        }
    }

    private void showStartupError(String message) {
        if (terminal != null) terminal.appendOutput("[AstraTerm] " + message + "\n");
        if (status != null) status.setText("PTY ERROR");
    }

    private void submit(EditText command) {
        String text = command.getText().toString();
        if (ctrlPending) {
            String control = controlCharacter(text);
            if (control != null) {
                write(control);
                ctrlPending = false;
                command.setText("");
                return;
            }
            ctrlPending = false;
        }
        write(text + "\n");
        command.setText("");
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(command.getWindowToken(), 0);
    }

    private String controlCharacter(String text) {
        if ("c".equalsIgnoreCase(text)) return "\u0003";
        if ("d".equalsIgnoreCase(text)) return "\u0004";
        if ("z".equalsIgnoreCase(text)) return "\u001a";
        return null;
    }

    private synchronized void write(String value) {
        FileOutputStream output = shellInput;
        if (!running.get() || output == null) return;
        try {
            output.write(value.getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (IOException e) {
            if (terminal != null) terminal.appendOutput("\n[PTY write error] " + e.getMessage() + "\n");
        }
    }

    private void closePty() {
        running.set(false);
        closeQuietly(shellInput);
        closeQuietly(shellOutput);
        closeQuietly(writePty);
        closeQuietly(readPty);
        closeQuietly(pty);
        shellInput = null;
        shellOutput = null;
        writePty = null;
        readPty = null;
        pty = null;
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Closing a duplicated PTY descriptor can race with reader shutdown.
        }
    }

    @Override
    protected void onDestroy() {
        closePty();
        terminal = null;
        status = null;
        super.onDestroy();
    }
}
