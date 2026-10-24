//
// Created by matadoryuki on 13/06/2024.
//

#ifndef INJECTOR_LITE_FREEFIRE_BASE_CONST_H
#define INJECTOR_LITE_FREEFIRE_BASE_CONST_H

#include <stdlib.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <string>
#include <vector>
#include <errno.h>
#include <stdio.h>
#include <sys/un.h>
#include <poll.h>

#define SOCKET_NAME "nin_X9K2q"
#define TCP_PORT 49221
#define TCP_HOST "127.0.0.1"
#define BACKLOG 8
#define PacketSize 4096
#define SOCKET_BUFFER_SIZE (256 * 1024) // 256 KB high-throughput buffer for 120 FPS ESP
#define SOCKET_TIMEOUT_MS 1000

#endif //INJECTOR_LITE_FREEFIRE_BASE_CONST_H
