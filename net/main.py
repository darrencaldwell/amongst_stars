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

HOST = "pc8-016-l.cs.st-andrews.ac.uk"
TCP_PORT = 25565
ENCODING = "utf-8"

rooms = {} # id: {client_a: {}, client_b: {}}
rooms_lock = threading.Lock()


def rx_json(socket):
    while True:
        data = socket.recv(4096).decode(ENCODING)
        if len(data) > 0:
            return json.loads(data)


def tx_json(socket, json_data):
    msg = bytes(json.dumps(json_data),ENCODING)
    socket.send(msg)


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
    tx_json(socket_a, data)
    tx_json(socket_b, data)

    # get tx UDP ports from clients
    player_a_udp_port = rx_json(socket_a)["port"]
    player_b_udp_port = rx_json(socket_b)["port"]

    print("new_room: server_port = " + str(port))
    print("new_room: client_a_udp = " + ip_addr_a + ":" + str(player_a_udp_port))
    print("new_room: client_b_udp = " + ip_addr_b + ":" + str(player_b_udp_port))

    # open tx sockets
    tx_udp_socket_a = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    tx_udp_socket_a.bind((ip_addr_a, player_a_udp_port))

    tx_udp_socket_b = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    tx_udp_socket_b.bind((ip_addr_b, player_b_udp_port))

    # send initial game state
    


## start udp listener
def thread_new_game(room_id, udp_socket):
    # set initial game state
    state = {
        "space_boy" : {
        },
        "wallee": {
        }
    }
    # send initial game state
    time.sleep(5)
    data = json.dumps(state).encode(ENCODING)
    udp_socket.sendall(bytes(data))
    # listen for updates every 10ms
    # send state


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