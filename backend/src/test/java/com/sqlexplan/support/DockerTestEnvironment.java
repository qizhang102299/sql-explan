package com.sqlexplan.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 在 Testcontainers 初始化前探测本机 Docker 套接字（尤其 macOS Docker Desktop）。
 */
public final class DockerTestEnvironment {

  private static final String DOCKER_HOST_PROPERTY = "DOCKER_HOST";

  private static final String TESTCONTAINERS_DOCKER_HOST = "docker.host";

  private static final String TESTCONTAINERS_SOCKET_OVERRIDE = "TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE";

  static {
    ensureDockerHostConfigured();
  }

  private DockerTestEnvironment() {}

  /**
   * 供 JUnit 套件在最早时机加载此类，触发 Docker 探测。
   */
  public static void touch() {
    // 触发静态初始化
  }

  private static void ensureDockerHostConfigured() {
    if (isConfigured()) {
      return;
    }
    Path socket = findDockerSocket();
    if (socket == null) {
      return;
    }
    String host = "unix://" + socket.toAbsolutePath();
    System.setProperty(TESTCONTAINERS_DOCKER_HOST, host);
    System.setProperty(TESTCONTAINERS_SOCKET_OVERRIDE, socket.toAbsolutePath().toString());
    if (System.getenv(DOCKER_HOST_PROPERTY) == null) {
      System.setProperty(DOCKER_HOST_PROPERTY, host);
    }
  }

  private static boolean isConfigured() {
    return System.getenv(DOCKER_HOST_PROPERTY) != null
        || System.getProperty(TESTCONTAINERS_DOCKER_HOST) != null
        || System.getProperty(DOCKER_HOST_PROPERTY) != null;
  }

  private static Path findDockerSocket() {
    List<Path> candidates = new ArrayList<>();
    String home = System.getProperty("user.home");
    if (home != null) {
      candidates.add(Path.of(home, ".docker", "run", "docker.sock"));
      candidates.add(
          Path.of(
              home,
              "Library",
              "Containers",
              "com.docker.docker",
              "Data",
              "docker-cli.sock"));
    }
    candidates.add(Path.of("/var/run/docker.sock"));
    for (Path candidate : candidates) {
      try {
        if (Files.exists(candidate) && Files.isReadable(candidate)) {
          return candidate;
        }
      } catch (Exception ignored) {
        // 继续探测下一候选路径
      }
    }
    return null;
  }
}
