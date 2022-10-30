### Start Listening for Connections on known port (give room id) ###

### On Connection, if no room id exist, create new room and wait for 2 players ###

### on both players connect, create udp sockets and reply with their udp port to connect to ###

### handle events

from audioop import add
import socket
from ssl import SOL_SOCKET
import threading
import time
import json

HOST = "pc8-015-l.cs.st-andrews.ac.uk"
TCP_PORT = 25565
ENCODING = "utf-8"

rooms = {} # id: {client_a: {}, client_b: {}}
rooms_lock = threading.Lock()


def rx_json(socket):
    while True:
        data = socket.recv(4096).decode(ENCODING).rstrip('\x00').rstrip('\n')
        if len(data) > 0:
            return json.loads(data)


def tx_json_tcp(socket, json_data):
    msg = bytes(json.dumps(json_data)+"\n",ENCODING)
    socket.sendall(msg)

def tx_json_udp(socket, address, port, json_data):
    msg = bytes(json.dumps(json_data)+"\n",ENCODING)
    socket.sendto(msg, (address,port))

def rx_json_udp(socket):
    data, addr = socket.recvfrom(4096)
    data = data.decode(ENCODING).rstrip('\x00').rstrip('\n')
    print("rx_json_udp: got udp rx from " + str(addr))
    json_data = json.loads(data)
    json_data['addr'] = addr
    return json_data


def thread_on_new_client(client_socket, addr):
    # wait for room number
    data = rx_json(client_socket)
    room_id = data['room_id']

    # determine if room_id already exists
    rooms_lock.acquire()

    if room_id not in rooms:
        print("on_new_client: player 1 joined room_id " + str(room_id))
        rooms[room_id] = [{"socket": client_socket, "addr": addr}]
        room_thread = threading.Thread(target=thread_new_room, args=(room_id,))
        room_thread.start()
    else:
        # if room full don't allow it
        if len(rooms[room_id]) == 2:
            msg = "ERROR: woah cow person... room " + str(room_id) + " already full, idiot.\n"
            client_socket.send(msg.encode(ENCODING))
            client_socket.close()
        else:
            print("on_new_client: player 2 joined room_id " + str(room_id))
            rooms[room_id].append({"socket": client_socket, "addr": addr})

    rooms_lock.release()


def thread_new_room(room_id):
    while len(rooms[room_id]) != 2:
        time.sleep(1)

    print("new_room: 2 clients on room_id = " + str(room_id))

    room = rooms[room_id]
    player_a = room[0]
    player_b = room[1]
    socket_a = player_a["socket"]
    ip_addr_a = player_a["addr"][0]
    socket_b = player_b["socket"]
    ip_addr_b = player_b["addr"][0]

    # start rx udp socket and send port to clients
    rx_udp_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    rx_udp_socket.bind((HOST, 0))
    port = rx_udp_socket.getsockname()[1]

    # send server UDP ports to clients
    data = {"server_port": port}
    tx_json_tcp(socket_a, data)
    tx_json_tcp(socket_b, data)

    # get tx UDP ports from clients
    player_a_udp_port = rx_json(socket_a)["port"]
    player_b_udp_port = rx_json(socket_b)["port"]

    print("new_room: server_port = " + str(port))
    print("new_room: client_a_udp = " + ip_addr_a + ":" + str(player_a_udp_port))
    print("new_room: client_b_udp = " + ip_addr_b + ":" + str(player_b_udp_port))

    # open tx sockets
    tx_udp_socket_a = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    tx_udp_socket_b = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

    data = {"hello": "cow person"}
    tx_json_udp(tx_udp_socket_a, ip_addr_a, player_a_udp_port, data)
    tx_json_udp(tx_udp_socket_b, ip_addr_b, player_b_udp_port, data)

    print(rx_json_udp(rx_udp_socket))

    # send initial state
    state = {
        "space_boy" : {
        },
        "wallee": {
        }
    }


    """
    {
        "other_x": 1,
        "other_y": 2,
        "angle": 23
    }
    
    """

    # busy loop
    player_a_state = {}
    player_b_state = {}
    while True:
        player_a_update = False
        player_b_update = False
        # read in packets until player a and b updated
        packet_data = rx_json_udp(rx_udp_socket)
        if packet_data['addr'] == ip_addr_a:
            player_a_update = True
            player_a_state = packet_data
        else:
            player_b_update = True
            player_b_state = packet_data
        
        if player_a_update and player_b_update:
            # send appropriate state to other player


    # send player a, b's info
    # send player b, a's info


def start_server():
    print("Starting Server...")
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind((HOST, TCP_PORT))
        s.listen()

        while True:
            conn, addr = s.accept()
            thread = threading.Thread(target = thread_on_new_client, args = (conn, addr))
            thread.start()


if __name__ == "__main__":
   start_server()
