#ifndef NIN_SHELL_EXEC_H
#define NIN_SHELL_EXEC_H

#include <spawn.h>
#include <sys/wait.h>
#include <unistd.h>
#include <fcntl.h>
#include <cerrno>
#include <cstddef>
#include <cstring>

extern char **environ;

/**
 * Runs a shell command WITHOUT using system() or popen().
 *
 * WHY THIS EXISTS
 * ---------------
 * bionic implements system() and popen() with a plain fork(). The Android
 * app sandbox does not allow that clone() variant: the seccomp filter of
 * the app zygote answers it with SIGSYS, which kills the whole process.
 * (posix_spawn() uses clone(CLONE_VM|CLONE_VFORK), the variant the sandbox
 * allows - the same mechanism Runtime.exec() / ProcessBuilder use, which is
 * why the Java side could always talk to su.)
 *
 * That is what killed the app on rooted devices: the global constructor of
 * the driver ran system("mknod ...") the moment System.loadLibrary() was
 * called, so the process died with SIGSYS while the splash screen was still
 * showing "Requesting Superuser Privileges".
 *
 * @param command the shell command line
 * @param output  optional buffer that receives the standard output
 * @param outLen  size of the output buffer (including the terminating NUL)
 * @return the exit status of the command, or -1 when it could not be run
 */
inline int RunShellCommand(const char *command, char *output, size_t outLen) {
    if (command == nullptr) {
        return -1;
    }

    int pipeFds[2] = {-1, -1};
    if (pipe(pipeFds) != 0) {
        return -1;
    }

    posix_spawn_file_actions_t actions;
    posix_spawn_file_actions_init(&actions);
    posix_spawn_file_actions_adddup2(&actions, pipeFds[1], STDOUT_FILENO);
    posix_spawn_file_actions_addclose(&actions, pipeFds[0]);
    posix_spawn_file_actions_addclose(&actions, pipeFds[1]);
    posix_spawn_file_actions_addopen(&actions, STDERR_FILENO, "/dev/null", O_WRONLY, 0);

    char *argv[] = {(char *)"/system/bin/sh", (char *)"-c", (char *)command, nullptr};

    pid_t pid = 0;
    int rc = posix_spawn(&pid, "/system/bin/sh", &actions, nullptr, argv, environ);

    posix_spawn_file_actions_destroy(&actions);
    close(pipeFds[1]);

    if (rc != 0) {
        close(pipeFds[0]);
        return -1;
    }

    if (output != nullptr && outLen > 0) {
        output[0] = '\0';
        size_t used = 0;
        ssize_t n;
        while (used + 1 < outLen &&
               (n = read(pipeFds[0], output + used, outLen - used - 1)) > 0) {
            used += (size_t)n;
        }
        output[used] = '\0';
    }
    close(pipeFds[0]);

    int status = 0;
    while (waitpid(pid, &status, 0) < 0 && errno == EINTR) {
        // retry on a signal
    }
    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    }
    return -1;
}

#endif // NIN_SHELL_EXEC_H
