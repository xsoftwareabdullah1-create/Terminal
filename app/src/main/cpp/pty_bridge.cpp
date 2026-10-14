#include <jni.h>
#include <cerrno>
#include <cstring>
#include <cstdlib>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

extern "C" JNIEXPORT jint JNICALL
Java_com_astraterm_mobile_MainActivity_nativeStartShell(JNIEnv* env, jclass, jstring homeString) {
    const char* homeChars = env->GetStringUTFChars(homeString, nullptr);
    if (!homeChars) return -1;
    char* home = strdup(homeChars);
    env->ReleaseStringUTFChars(homeString, homeChars);
    if (!home) return -1;

    int master = posix_openpt(O_RDWR | O_NOCTTY | O_CLOEXEC);
    if (master < 0) { free(home); return -errno; }
    if (grantpt(master) != 0 || unlockpt(master) != 0) {
        int error = errno; close(master); free(home); return -error;
    }
    char* slaveName = ptsname(master);
    if (!slaveName) { int error = errno; close(master); free(home); return -error; }

    struct winsize ws {};
    ws.ws_row = 24;
    ws.ws_col = 80;
    ioctl(master, TIOCSWINSZ, &ws);

    pid_t child = fork();
    if (child < 0) { int error = errno; close(master); free(home); return -error; }
    if (child == 0) {
        close(master);
        setsid();
        int slave = open(slaveName, O_RDWR);
        if (slave < 0) _exit(126);
        ioctl(slave, TIOCSCTTY, 0);
        dup2(slave, STDIN_FILENO);
        dup2(slave, STDOUT_FILENO);
        dup2(slave, STDERR_FILENO);
        if (slave > STDERR_FILENO) close(slave);
        setenv("HOME", home, 1);
        setenv("TMPDIR", "/data/local/tmp", 1);
        setenv("PATH", "/system/bin:/system/xbin", 1);
        setenv("TERM", "xterm-256color", 1);
        chdir(home);
        execl("/system/bin/sh", "sh", "-i", static_cast<char*>(nullptr));
        _exit(127);
    }
    free(home);
    return master;
}
