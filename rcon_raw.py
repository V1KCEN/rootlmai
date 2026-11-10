import socket
import struct
import time

HOST = "127.0.0.1"
PORT = 25575
PASSWORD = b"rootimpact"


def recv_exact(sock, n):
    data = b""
    while len(data) < n:
        chunk = sock.recv(n - len(data))
        if not chunk:
            break
        data += chunk
    return data


for attempt in range(1, 6):
    try:
        sock = socket.create_connection((HOST, PORT), timeout=10)
        sock.settimeout(10)
        body = struct.pack("<ii", 1, 3) + struct.pack(">H", len(PASSWORD)) + PASSWORD + b"\x00\x00"
        packet = struct.pack("<i", len(body) + 4) + body
        sock.sendall(packet)
        header = recv_exact(sock, 4)
        print("attempt", attempt, "header:", header.hex(), flush=True)
        if len(header) == 4:
            size = struct.unpack("<i", header)[0]
            body = recv_exact(sock, size)
            print("  size:", size, "body:", body.hex(), "text:", body[8:-2], flush=True)
        sock.close()
    except Exception as exc:
        print("attempt", attempt, "ERR:", exc, flush=True)
    time.sleep(1)
