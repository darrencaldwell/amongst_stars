import processing.core.PApplet
import processing.core.PVector
import org.json.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.util.*
import javax.xml.crypto.Data
import kotlin.math.cos
import kotlin.math.sin
import java.util.InputMismatchException
import kotlin.math.*


// game loop driver fun
//override fun draw() {
//
////    when (state) {
////        GAMESTATE.PREGAME  -> landingScreen()
////        GAMESTATE.PREWAVE  -> prewaveScreen()
////        GAMESTATE.WAVE     -> game.draw()
////        GAMESTATE.ENDWAVE -> endWave.draw()
////        GAMESTATE.GAMEOVER -> gameoverScreen()
////    }
//}

const val EARTH_RADIUS = 100f
const val HOST = "pc8-015-l.cs.st-andrews.ac.uk"
const val HOST_TCP_PORT = 25565
const val MAGIC_ROOM_ID = 1337
const val ENABLE_MULTIPLAYER = false
var gameScreen = 0
// ms
var TIME_SINCE_LAST_STATE_UPDATE = 100

operator fun PVector.times(other: Float) = PVector.mult(this, other)
operator fun PVector.plus(other: PVector) = PVector.add(this, other)

data class Input(
    var isUp: Boolean = false,
    var isDown: Boolean = false,
    var isLeft: Boolean = false,
    var isRight: Boolean = false,
)
typealias SphericalCoords = PVector
fun SphericalCoords.r() = this.z
fun SphericalCoords.theta() = this.x
fun SphericalCoords.phi() = this.y



fun SphericalCoords.toXyz(): PVector {
    val x1 = sin(theta()) * cos(phi())
    val y1 = sin(theta()) * sin(phi())
    val z1 = cos(theta())
    val ret = PVector(x1,y1,z1)
    ret.mult(r())
    return ret
}

fun PVector.toSpherical(): SphericalCoords {
    val r = sqrt(x.pow(2) + y.pow(2) + z.pow(2))
    val theta = atan(y/x)
    val phi = acos(z/r)
    return SphericalCoords(theta, phi, r)
}

data class WallE(val pos: SphericalCoords, val size: Float = 5f, var rot: Float = 0f) {
    fun draw(app: PApplet) {
        val position = pos.toXyz()
//        val posToUp = position.cross(PVector(0f,1f,0f))

        with(app) {
            pushMatrix()
            translate(position.x,position.y,position.z)
            circle(0f,0f,size)
            popMatrix()
        }
    }

    fun update(input: Input) {
        val movementSpeed = 0.01f
        val lookSpeed = 0.01f

//        var isMoving = false
//        when {
////            input.isUp -> isMoving = true
////            input.isDown -> pos.x -= movementSpeed
////            input.isLeft -> rot += lookSpeed
////            input.isRight -> rot -= lookSpeed
//            input.isUp -> pos.x += movementSpeed
//            input.isDown -> pos.x -= movementSpeed
//            input.isLeft -> pos.y += lookSpeed
//            input.isRight -> pos.y -= lookSpeed
//        }


//         actually move!!!
//        val wallePos = pos.toXyz()
//        val u = wallePos.copy().normalize()
////        val x = wallePos.copy().add(PVector(0.000000000000000000000000001f,0f,0f))
////        val x = u
////        val newPosCart = u * u.dot(x) + u.cross(x) * cos(rot) + u.cross(x) * sin(rot)
//        val a = wallePos.add(PVector(0.01f, 0f, 0f))

        pos.set(pos.toXyz().add(0f,0f,1f).toSpherical())
//        pos.set(wallePos.toSpherical())

    }
}




/**
 * The driver defining the flow of the game,
 * handling input, render control, etc
 */
class Game : PApplet() {

    enum class CameraMode {
        TOPDOWN,
        BOTTOMUP,
        FPS,
    }

    val cameraMode: CameraMode = CameraMode.TOPDOWN
    val input: Input = Input()

    val wallE = WallE(PVector(0f,0f, EARTH_RADIUS+2f))
    val moon = WallE(PVector(0f, 0f, EARTH_RADIUS*2f), 50f)

    override fun setup() {

    }

    override fun settings() {
        fullScreen(P3D)
    }


    fun camTopDown() {
        val cartWallE = wallE.pos.toXyz()
        val centre = cartWallE
        val eyepos = cartWallE * 2f
        val up = PVector(0f,1f,0f)
        camera(eyepos.x, eyepos.y, eyepos.z, centre.x, centre.y, centre.z, up.x,up.y,up.z)
    }

    fun camBottomUp() {
        val cartWallE = wallE.pos.toXyz()
        val eyepos = cartWallE
        val centre = cartWallE * 2f
        val up = PVector(0f,1f,0f)
        camera(eyepos.x, eyepos.y, eyepos.z, centre.x, centre.y, centre.z, up.x,up.y,up.z)
    }

    fun camFps() {
        val cartWallE = wallE.pos.toXyz()
        val eyepos = cartWallE
        val centre = cartWallE * 2f
        val up = TODO() // needs to be walle up ie pos.XYZ.norm
//        camera(eyepos.x, eyepos.y, eyepos.z, centre.x, centre.y, centre.z, up.x,up.y,up.z)
    }

    fun setupCam() {

        when (cameraMode) {
            CameraMode.TOPDOWN -> camTopDown()
            CameraMode.BOTTOMUP -> camBottomUp()
            CameraMode.FPS -> camFps()
        }
    }

    override fun keyPressed() {
        if (key.code == CODED) {
            when (keyCode) {
                UP -> input.isUp = true
                DOWN -> input.isDown = true
                LEFT -> input.isLeft = true
                RIGHT -> input.isRight = true
            }
        }
    }

    override fun keyReleased() {
        if (key.code == CODED) {
            when (keyCode) {
                UP -> input.isUp = false
                DOWN -> input.isDown = false
                LEFT -> input.isLeft = false
                RIGHT -> input.isRight = false
            }
        }
    }

    fun homeScreen() {
        background(0);
        textAlign(CENTER);
        text("Click to start", height/2.toFloat(), width/2.toFloat());
    }
    fun gameScreen() {
        background(-1f)
        val eyepos =

//        camera(cartWallE.x, cartWallE.y, cartWallE.z, eyepos.x, eyepos.y, eyepos.z, up.x,up.y,up.z)
            setupCam()
        pushMatrix()
//        val pos = PVector(width.toFloat()/1f, height.toFloat()/2f,100f)
//        with(pos) {
//            translate(x,y,z)
//        }
        fill(-1f, 0f, 200f)
        sphere(EARTH_RADIUS)
        fill(254f)
        wallE.draw(this)
        moon.draw(this)
        moon.pos.x += -1.01f
        wallE.update(input)
        popMatrix()
    }


    override fun mousePressed() {
        // if we are on the initial screen when clicked, start the game
        if (gameScreen == 0) {
            startGame()
        }
    }


    fun startGame() {
        // Handle Network handshake then change gameScreen to 1
        kotlin.io.println("Trying to start game?")

        if (ENABLE_MULTIPLAYER) {
            // init tcp connection to server
            // connect to magic number port and host over TCP
            val server_tcp_socket = Socket(HOST, HOST_TCP_PORT)
            // send magic room number to server
            server_tcp_socket.outputStream.write(("{\"room_id\":$MAGIC_ROOM_ID}").toByteArray())
            // wait... get udp port number to open a tx socket to
            val scanner = Scanner(server_tcp_socket.getInputStream())
            var msg = ""
            while (scanner.hasNextLine()) {
                msg = scanner.nextLine()
                break
            }
            val json_msg = JSONObject(msg)
            val server_udp_port: Int = json_msg["server_port"] as Int

            val server_udp_socket = DatagramSocket()
            //server_udp_socket.connect(InetAddress.getByName(HOST), server_udp_port)
            // open local udp socket and connect to remove server udp port
            val client_udp_port = server_udp_socket.localPort
            // send our port to server
            server_tcp_socket.outputStream.write(("{\"port\":$client_udp_port}").toByteArray())

            // rx initial game state
            val buffer = ByteArray(4096)
            val packet = DatagramPacket(buffer, buffer.size)
            server_udp_socket.receive(packet)
            // TODO: parse game state and use it to init next screen
            println(JSONObject(String(packet.data)))

            ///  Example tx echo to server
            //val tx_packet = DatagramPacket(packet.data, packet.data.size, InetAddress.getByName(HOST), server_udp_port)
            //server_udp_socket.send(tx_packet)

            // TODO: gameloop should include reading from UDP socket and writing state to server
        }

        gameScreen = 1
    }

    override fun draw() {

        if (gameScreen == 0) {
            homeScreen()
        } else {
            gameScreen()
        }
    }


    companion object Factory {
        fun run() {
            val instance = Game()
            instance.runSketch()
        }
    }
}


fun main(args: Array<String>) {

    Game.run()
    println("Hello World!")

    // Try adding program arguments via Run/Debug configuration.
    // Learn more about running applications: https://www.jetbrains.com/help/idea/running-applications.html.
    println("Program arguments: ${args.joinToString()}")
}

//Update yaw (note that this is done using a rotation about the local up axis):
// person.rotate_about_local_up(yaw_delta);

// Update the position. This will move the player tangent to the sphere, so after this step/
// the player will be 'floating' above the sphere a bit.
// person.position += person.forward * speed * time_step;

// Get the sphere normal corresponding to the point directly under the player:vector
// normal = normalize(person.position);

// Drop the player back down to the surface:person.position = normal * sphere.radius;
// Now the person is on the surface, but probably isn't perfectly 'upright' with respect
// to it, so we apply a normalizing relative rotation to correct this:
// matrix rotation = matrix_rotate_vec_to_vec(person.up, normal);
// person.apply_rotation(rotation);