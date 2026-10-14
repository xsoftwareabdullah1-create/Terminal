package com.astraterm.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.content.Context;
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
    static { System.loadLibrary("astraterm"); }
    private static native int nativeStartShell(String home);

    private TerminalView terminal;
    private TextView status;
    private ParcelFileDescriptor pty;
    private FileOutputStream shellInput;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean ctrlPending = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        terminal = findViewById(R.id.terminal);
        status = findViewById(R.id.status);
        EditText command = findViewById(R.id.commandInput);
        Button send = findViewById(R.id.send);

        send.setOnClickListener(v -> submit(command));
        command.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                submit(command);
                return true;
            }
            return false;
        });

        findViewById(R.id.keyCtrl).setOnClickListener(v -> { ctrlPending = !ctrlPending; status.setText(ctrlPending ? "CTRL READY" : "CONNECTED"); });
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
        if (!home.exists() && !home.mkdirs()) {
            terminal.appendOutput("Unable to create private home directory.\n");
            status.setText("HOME ERROR");
            return;
        }
        int fd = nativeStartShell(home.getAbsolutePath());
        if (fd < 0) {
            terminal.appendOutput("PTY startup failed (errno " + (-fd) + ").\n");
            status.setText("PTY ERROR");
            return;
        }
        try {
            pty = ParcelFileDescriptor.adoptFd(fd);
            shellInput = new FileOutputStream(pty.getFileDescriptor());
            running.set(true);
            status.setText("CONNECTED");
            Thread reader = new Thread(() -> {
                byte[] buffer = new byte[4096];
                try (FileInputStream input = new FileInputStream(pty.getFileDescriptor())) {
                    int count;
                    while (running.get() && (count = input.read(buffer)) != -1) {
                        String text = new String(buffer, 0, count, StandardCharsets.UTF_8);
                        runOnUiThread(() -> terminal.appendOutput(text));
                    }
                } catch (IOException e) {
                    if (running.get()) runOnUiThread(() -> terminal.appendOutput("\n[PTY] " + e.getMessage() + "\n"));
                } finally {
                    running.set(false);
                    runOnUiThread(() -> status.setText("SHELL EXITED"));
                }
            }, "AstraTerm-pty-reader");
            reader.setDaemon(true);
            reader.start();
        } catch (Exception e) {
            running.set(false);
            status.setText("PTY ERROR");
            terminal.appendOutput("Unable to connect PTY: " + e.getMessage() + "\n");
        }
    }

    private void submit(EditText command) {
        String text = command.getText().toString();
        if (text.isEmpty()) { write("\n"); return; }
        write((ctrlPending ? controlify(text) : text) + "\n");
        ctrlPending = false;
        command.setText("");
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(command.getWindowToken(), 0);
    }

    private String controlify(String text) {
        if (text.equalsIgnoreCase("c")) return "\u0003";
        if (text.equalsIgnoreCase("d")) return "\u0004";
        if (text.equalsIgnoreCase("z")) return "\u001a";
        return text;
    }

    private synchronized void write(String value) {
        try {
            if (running.get() && shellInput != null) {
                shellInput.write(value.getBytes(StandardCharsets.UTF_8));
                shellInput.flush();
            }
        } catch (IOException e) {
            terminal.appendOutput("\n[write error] " + e.getMessage() + "\n");
        }
    }

    @Override protected void onDestroy() {
        running.set(false);
        try { if (pty != null) pty.close(); } catch (IOException ignored) {}
        super.onDestroy();
    }
}
