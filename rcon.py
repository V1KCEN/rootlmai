import socket
import struct
import sys
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


def send(sock, req_id, req_type, payload):
    body = struct.pack("<ii", req_id, req_type) + payload + b"\x00\x00"
    packet = struct.pack("<i", len(body)) + body
    sock.sendall(packet)


def recv(sock):
    header = recv_exact(sock, 4)
    if len(header) < 4:
        return None, None
    size = struct.unpack("<i", header)[0]
    body = recv_exact(sock, size)
    if len(body) < size:
        return None, None
    req_id, req_type = struct.unpack("<ii", body[:8])
    payload = body[8:-2].decode("utf-8", errors="replace")
    return req_id, payload


def main():
    cmds_file = sys.argv[1]
    pause = float(sys.argv[2]) if len(sys.argv) > 2 else 0.0
    with open(cmds_file, "r", encoding="utf-8") as fh:
        commands = [line.strip() for line in fh if line.strip()]
    sock = socket.create_connection((HOST, PORT), timeout=20)
    sock.settimeout(20)
    send(sock, 1, 3, PASSWORD)
    auth = recv(sock)
    print("AUTH ->", auth, flush=True)
    if auth[0] is None:
        return
    rid = 10
    for cmd in commands:
        try:
            send(sock, rid, 2, cmd.encode("utf-8"))
        except Exception as exc:
            print("SEND FAIL:", exc, flush=True)
            return
        while True:
            try:
                got_id, payload = recv(sock)
            except Exception as exc:
                print("RECV FAIL:", exc, flush=True)
                return
            if got_id is None:
                print("!! closed", flush=True)
                return
            if got_id == rid:
                print("> " + cmd, flush=True)
                if payload:
                    print("  " + payload.replace("\n", "\n  "), flush=True)
                break
        rid += 1
        if pause:
            time.sleep(pause)
    sock.close()


if __name__ == "__main__":
    main()
