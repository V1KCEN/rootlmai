"""Минимальный Minecraft-протокольный бот для dev-сервера ROOT IMPACT."""
import socket
import struct
import sys
import time
import zlib

HOST = "127.0.0.1"
PORT = 25565
USERNAME = sys.argv[1] if len(sys.argv) > 1 else "Dev"
DURATION = float(sys.argv[3]) if len(sys.argv) > 3 else 60.0

PROTOCOL_VERSION = 767

CB_LOGIN_SUCCESS = 0x02
CB_LOGIN_SET_COMPRESSION = 0x03
SB_LOGIN_HELLO = 0x00
SB_LOGIN_ACK = 0x03
CB_CONF_FINISH = 0x03
SB_CONF_FINISH = 0x03
SB_CONF_CLIENT_INFO = 0x00
CB_PLAY_KEEP_ALIVE = 0x26
SB_PLAY_KEEP_ALIVE = 0x18

threshold = -1
sock = None
DEBUG = True


def read_varint(data, pos):
    result = 0
    for i in range(5):
        if pos >= len(data):
            raise EOFError("varint truncated")
        byte = data[pos]
        pos += 1
        result |= (byte & 0x7F) << (7 * i)
        if not byte & 0x80:
            return result, pos
    raise ValueError("varint too long")


def write_varint(value):
    out = bytearray()
    while True:
        byte = value & 0x7F
        value >>= 7
        if value:
            out.append(byte | 0x80)
        else:
            out.append(byte)
            return bytes(out)


def write_string(text):
    raw = text.encode("utf-8")
    return write_varint(len(raw)) + raw


def recv_exact(n):
    data = b""
    while len(data) < n:
        chunk = sock.recv(n - len(data))
        if not chunk:
            raise EOFError("connection closed")
        data += chunk
    return data


def send_packet(packet_id, payload):
    body = write_varint(packet_id) + payload
    if threshold < 0:
        sock.sendall(write_varint(len(body)) + body)
    elif len(body) >= threshold:
        compressed = zlib.compress(body)
        sock.sendall(write_varint(len(compressed)) + write_varint(len(body)) + compressed)
    else:
        sock.sendall(write_varint(len(body) + 1) + b"\x00" + body)


def read_varint_from_socket():
    result = 0
    for i in range(5):
        byte = recv_exact(1)[0]
        result |= (byte & 0x7F) << (7 * i)
        if not byte & 0x80:
            break
    return result


def read_packet():
    length = read_varint_from_socket()
    data = recv_exact(length)
    if DEBUG:
        print("[bot] raw packet: len=%d first=%s" % (length, data[:16].hex()), flush=True)
    if threshold >= 0:
        data_len, pos = read_varint(data, 0)
        if data_len != 0:
            data = zlib.decompress(data[pos:])
        else:
            data = data[pos:]
    packet_id, pos = read_varint(data, 0)
    return packet_id, data[pos:]


def raw_send(packet_id, payload):
    body = write_varint(packet_id) + payload
    sock.sendall(write_varint(len(body)) + body)

def handshake():
    payload = (
        write_varint(PROTOCOL_VERSION)
        + write_string(HOST)
        + struct.pack(">H", PORT)
        + write_varint(2)
    )
    raw_send(0x00, payload)


def login_phase():
    global threshold
    payload = write_string(USERNAME) + b"\x00" * 16
    raw_send(SB_LOGIN_HELLO, payload)

    while True:
        packet_id, data = read_packet()
        if packet_id == CB_LOGIN_SUCCESS:
            print("[bot] login success", flush=True)
            send_packet(SB_LOGIN_ACK, b"")
            return
        if packet_id == CB_LOGIN_SET_COMPRESSION:
            threshold, _ = read_varint(data, 0)
            print("[bot] compression threshold:", threshold, flush=True)
        elif packet_id == 0x00:
            print("[bot] login disconnect:", data[:200], flush=True)
            raise SystemExit(1)
        else:
            print("[bot] login packet 0x%02x ignored" % packet_id, flush=True)


def client_information():
    payload = bytearray()
    payload += write_string("en_US")
    payload.append(8)                  # view distance
    payload += write_varint(0)         # chat mode: enabled
    payload += b"\x01"                 # chat colors
    payload += bytes([0x7F])           # displayed skin parts
    payload += write_varint(1)         # main hand: right
    payload += b"\x00"                 # text filtering
    payload += b"\x01"                 # allow listing
    send_packet(SB_CONF_CLIENT_INFO, bytes(payload))


def configuration_phase():
    while True:
        packet_id, data = read_packet()
        if packet_id == 0x02:
            print("[bot] DISCONNECT reason:", data[1:600], flush=True)
            raise SystemExit(2)
        if packet_id == 0x00:
            print("[bot] cookie request:", data[:80], flush=True)
            send_packet(0x01, write_varint(0) + b"\x00")
        if packet_id == 0x01:
            print("[bot] custom payload:", data[:80], flush=True)
        if packet_id == 0x03:
            print("[bot] configuration finished", flush=True)
            send_packet(SB_CONF_FINISH, b"")
            return
        if packet_id == 0x04:
            value = struct.unpack(">q", data[:8])[0]
            send_packet(0x04, struct.pack(">q", value))
        elif packet_id == 0x05:
            value = struct.unpack(">i", data[:4])[0]
            send_packet(0x05, struct.pack(">i", value))
        elif packet_id == 0x07:
            print("[bot] registry data (%d bytes) received" % len(data), flush=True)
        elif packet_id == 0x0e:
            print("[bot] select known packs -> reply empty list", flush=True)
            send_packet(0x07, write_varint(0))
        elif packet_id == 0x09:
            print("[bot] resource pack push -> declined", flush=True)
            send_packet(0x06, data[:16] + write_varint(1))
        else:
            print("[bot] config packet 0x%02x (%d bytes)" % (packet_id, len(data)), flush=True)


def play_phase(deadline):
    seen = {}
    while time.time() < deadline:
        sock.settimeout(5.0)
        try:
            packet_id, data = read_packet()
        except (socket.timeout, TimeoutError):
            continue
        except EOFError:
            print("[bot] connection closed by server", flush=True)
            return
        if packet_id == CB_PLAY_KEEP_ALIVE:
            value = struct.unpack(">q", data[:8])[0]
            send_packet(SB_PLAY_KEEP_ALIVE, struct.pack(">q", value))
        else:
            seen[packet_id] = seen.get(packet_id, 0) + 1
    print("[bot] play packets seen:", {hex(k): v for k, v in sorted(seen.items())}, flush=True)


def run_script(path):
    """Выполняет сценарий пакетов после входа в игру."""
    with open(path, "r", encoding="utf-8") as fh:
        lines = [line.strip() for line in fh if line.strip() and not line.startswith("#")]
    for line in lines:
        parts = line.split()
        action = parts[0]
        if action == "wait":
            time.sleep(float(parts[1]))
            continue
        if action == "slot":
            slot, item_id, count = int(parts[1]), parts[2], int(parts[3])
            payload = struct.pack(">h", slot)
            if count > 0:
                payload += b"\x01"
                payload += write_varint(count)
                payload += write_string(item_id)
                payload += write_varint(0)
                payload += write_varint(0)
            else:
                payload += b"\x00"
            print("[bot] -> setCreativeModeSlot(slot=%d, %s x%d)" % (slot, item_id, count), flush=True)
            send_packet(0x32, payload)
        elif action == "pick":
            slot = int(parts[1])
            print("[bot] -> pickItem(slot=%d)" % slot, flush=True)
            send_packet(0x20, write_varint(slot))
        elif action == "drop":
            item_id, count = parts[1], int(parts[2])
            payload = b"\x01"
            payload += write_varint(count)
            payload += write_string(item_id)
            payload += write_varint(0)
            payload += write_varint(0)
            payload += b"\x00"
            print("[bot] -> dropItem(%s x%d)" % (item_id, count), flush=True)
            send_packet(0x1A, payload)
        time.sleep(0.4)


def main():
    global sock
    sock = socket.create_connection((HOST, PORT), timeout=30)
    sock.settimeout(30)
    print("[bot] connected as", USERNAME, flush=True)
    handshake()
    login_phase()
    client_information()
    configuration_phase()
    print("[bot] in play state", flush=True)
    if len(sys.argv) > 2 and sys.argv[2].endswith(".txt"):
        run_script(sys.argv[2])
        print("[bot] script done", flush=True)
    duration = float(sys.argv[3]) if len(sys.argv) > 3 else DURATION
    play_phase(time.time() + duration)
    sock.close()
    print("[bot] done", flush=True)


if __name__ == "__main__":
    try:
        main()
    except SystemExit:
        pass
    except Exception as exc:
        print("[bot] ERROR:", exc, flush=True)
        raise SystemExit(1)

