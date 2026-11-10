"""Бот ROOT IMPACT: подключается к dev-серверу и выполняет сценарий пакетов."""
import socket
import struct
import sys
import time
import zlib

HOST = "127.0.0.1"
PORT = 25565
USERNAME = sys.argv[1] if len(sys.argv) > 1 else "Dev"
SCRIPT = sys.argv[2] if len(sys.argv) > 2 else None
DURATION = float(sys.argv[3]) if len(sys.argv) > 3 else 90.0

PROTOCOL_VERSION = 767

CB_LOGIN_SUCCESS = 0x02
CB_LOGIN_SET_COMPRESSION = 0x03
SB_LOGIN_HELLO = 0x00
SB_LOGIN_ACK = 0x03
SB_CONF_CLIENT_INFO = 0x00
SB_CONF_FINISH = 0x03
CB_PLAY_KEEP_ALIVE = 0x26
SB_PLAY_KEEP_ALIVE = 0x18
SB_PLAY_SET_CREATIVE_SLOT = 0x32
SB_PLAY_PICK_ITEM = 0x20
SB_PLAY_DROP_ITEM = 0x1A

threshold = -1
sock = None
DEBUG = False


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
    if threshold >= 0:
        data_len, pos = read_varint(data, 0)
        data = zlib.decompress(data[pos:]) if data_len != 0 else data[pos:]
    packet_id, pos = read_varint(data, 0)
    return packet_id, data[pos:]


def raw_send(packet_id, payload):
    body = write_varint(packet_id) + payload

def handshake():
    payload = (
        write_varint(PROTOCOL_VERSION)
        + write_string(HOST)
        + struct.pack(">H", PORT)
        + write_varint(2)
    )
    print("[bot] sending handshake", flush=True)
    raw_send(0x00, payload)
    print("[bot] handshake sent", flush=True)


def login_phase():
    global threshold
    payload = write_string(USERNAME) + b"\x00" * 16
    print("[bot] sending login start", flush=True)
    raw_send(SB_LOGIN_HELLO, payload)
    print("[bot] login start sent, waiting", flush=True)
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


def client_information():
    payload = bytearray()
    payload += write_string("en_US")
    payload.append(8)
    payload += write_varint(0)
    payload += b"\x01"
    payload += bytes([0x7F])
    payload += write_varint(1)
    payload += b"\x00"
    payload += b"\x01"
    send_packet(SB_CONF_CLIENT_INFO, bytes(payload))


def configuration_phase():
    while True:
        packet_id, data = read_packet()
        if packet_id == 0x02:
            print("[bot] DISCONNECT reason:", data[1:600], flush=True)
            raise SystemExit(2)
        if packet_id == 0x00:
            send_packet(0x01, write_varint(0) + b"\x00")
        if packet_id == 0x03:
            print("[bot] configuration finished", flush=True)
            send_packet(SB_CONF_FINISH, b"")
            return
        if packet_id == 0x04:
            send_packet(0x04, struct.pack(">q", struct.unpack(">q", data[:8])[0]))
        elif packet_id == 0x05:
            send_packet(0x05, struct.pack(">i", struct.unpack(">i", data[:4])[0]))
        elif packet_id == 0x0e:
            print("[bot] select known packs -> empty", flush=True)
            send_packet(0x07, write_varint(0))
        elif packet_id == 0x09:
            send_packet(0x06, data[:16] + write_varint(1))


def run_script(path):
    with open(path, "r", encoding="utf-8") as fh:
        lines = [line.strip() for line in fh if line.strip() and not line.startswith("#")]
    for line in lines:
        parts = line.split()
        action = parts[0]
        if action == "wait":
            time.sleep(float(parts[1]))
        elif action == "slot":
            creative_slot(int(parts[1]), parts[2], int(parts[3]))
        elif action == "pick":
            pick_item(int(parts[1]))
        elif action == "drop":
            drop_item(parts[1], int(parts[2]))
        else:
            print("[bot] unknown script action:", action, flush=True)
        time.sleep(0.4)


def play_phase(deadline):
    while time.time() < deadline:
        sock.settimeout(1.0)
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
        elif packet_id == 0x1c:
            print("[bot] play disconnect:", data[1:300], flush=True)
            return


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
    if SCRIPT:
        run_script(SCRIPT)
        print("[bot] script done", flush=True)
    play_phase(time.time() + DURATION)
    sock.close()
    print("[bot] done", flush=True)


def item_stack_payload(item_id, count):
    if count <= 0:
        return write_varint(0)
    payload = write_varint(count)
    payload += write_string(item_id)
    payload += write_varint(0)   # components removed
    payload += write_varint(0)   # components added
    return payload


def creative_slot(slot, item_id, count):
    print("[bot] -> setCreativeModeSlot(slot=%d, %s x%d)" % (slot, item_id, count), flush=True)
    send_packet(SB_PLAY_SET_CREATIVE_SLOT, write_varint(slot) + item_stack_payload(item_id, count))


def pick_item(slot):
    print("[bot] -> pickItem(slot=%d)" % slot, flush=True)
    send_packet(SB_PLAY_PICK_ITEM, write_varint(slot))


def drop_item(item_id, count):
    print("[bot] -> dropItem(%s x%d)" % (item_id, count), flush=True)
    send_packet(SB_PLAY_DROP_ITEM, item_stack_payload(item_id, count) + b"\x00")


if __name__ == "__main__":
    try:
        main()
    except SystemExit:
        pass
    except Exception as exc:
        print("[bot] ERROR:", exc, flush=True)
        raise SystemExit(1)
