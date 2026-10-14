#include <jni.h>

#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <termios.h>
#include <unistd.h>

namespace {
char* copyJavaString(JNIEnv* env, jstring value) {
    if (value == nullptr) return nullptr;
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return nullptr;
    char* copy = strdup(chars);
    env->ReleaseStringUTFChars(value, chars);
    return copy;
}
}

extern "C" JNIEXPORT jint JNICALL
Java_com_astraterm_mobile_MainActivity_nativeStartShell(
        JNIEnv* env, jclass, jstring homeString, jstring tempString) {
    char* home = copyJavaString(env, homeString);
    char* temp = copyJavaString(env, tempString);
    if (home == nullptr || temp == nullptr) {
        free(home);
        free(temp);
        return -EINVAL;
    }

    const int master = posix_openpt(O_RDWR | O_NOCTTY | O_CLOEXEC);
    if (master < 0) {
        const int error = errno;
        free(home);
        free(temp);
        return -error;
    }

    if (grantpt(master) != 0 || unlockpt(master) != 0) {
        const int error = errno;
        close(master);
        free(home);
        free(temp);
        return -error;
    }

    char* slaveName = ptsname(master);
    if (slaveName == nullptr) {
        const int error = errno;
        close(master);
        free(home);
        free(temp);
        return -error;
    }

    struct winsize windowSize {};
    windowSize.ws_row = 24;
    windowSize.ws_col = 80;
    (void) ioctl(master, TIOCSWINSZ, &windowSize);

    const pid_t child = fork();
    if (child < 0) {
        const int error = errno;
        close(master);
        free(home);
        free(temp);
        return -error;
    }

    if (child == 0) {
        (void) close(master);
        if (setsid() < 0) _exit(126);

        const int slave = open(slaveName, O_RDWR);
        if (slave < 0) _exit(126);
        if (ioctl(slave, TIOCSCTTY, 0) < 0) {
            (void) close(slave);
            _exit(126);
        }

        if (dup2(slave, STDIN_FILENO) < 0
                || dup2(slave, STDOUT_FILENO) < 0
                || dup2(slave, STDERR_FILENO) < 0) {
            _exit(126);
        }
        if (slave > STDERR_FILENO) (void) close(slave);

        (void) setenv("HOME", home, 1);
        (void) setenv("TMPDIR", temp, 1);
        (void) setenv("PATH", "/system/bin:/system/xbin", 1);
        (void) setenv("TERM", "xterm-256color", 1);
        (void) setenv("LANG", "C.UTF-8", 1);
        (void) chdir(home);

        execl("/system/bin/sh", "sh", "-i", static_cast<char*>(nullptr));
        _exit(127);
    }

    free(home);
    free(temp);
    return master;
}
