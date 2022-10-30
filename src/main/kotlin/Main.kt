import processing.core.PApplet
import processing.core.PVector
import org.json.*
import java.net.*
import java.util.*
import javax.xml.crypto.Data
import kotlin.math.cos
import kotlin.math.sin
import processing.core.PVector.dist
import java.util.InputMismatchException
import kotlin.math.*
import kotlin.random.Random


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
const val HOST = "pc8-016-l.cs.st-andrews.ac.uk"
const val HOST_TCP_PORT = 25565
const val MAGIC_ROOM_ID = 1337
const val ENABLE_MULTIPLAYER = true
var gameScreen = 0
var timeSinceLastUpdate: Long = 0
// ms
const val TIME_BETWEEN_PACKET_UPDATE: Long = 1 * 1000000

var server_udp_port: Int = -1
val server_udp_socket = DatagramSocket()
val tx_udp_socket = DatagramSocket()

var player = -1

var other_theta = -1F
var other_phi = -1F
var bomb = false

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
    val theta = atan(y / x)

    val phi = acos(z / r)
    return SphericalCoords(theta, phi, r)
}

fun PVector.rotateAngleAxis(theta: Float, axis: PVector): PVector {
    val axisNormalised = axis.copy().normalize()
    val u = axisNormalised.x
    val v = axisNormalised.y
    val w = axisNormalised.z

    val xPrime =
        u * (u * x + v * y + w * z) * (1.0 - cos(theta)) + x * cos(theta) + (-w * y + v * z) * sin(
            theta
        )
    val yPrime =
        v * (u * x + v * y + w * z) * (1.0 - cos(theta)) + y * cos(theta) + (w * x - u * z) * sin(
            theta
        )
    val zPrime =
        w * (u * x + v * y + w * z) * (1.0 - cos(theta)) + z * cos(theta) + (-v * x + u * y) * sin(
            theta
        )
    return PVector(xPrime.toFloat(), yPrime.toFloat(), zPrime.toFloat())

}

data class Enemy(val pos:SphericalCoords, val size: Float=5f, var rot: Float=0f) {
    fun draw(app : PApplet) {
        drawThings(pos, size, app)
    }

    fun update(wallE: WallE) {
        val movementSpeed = 0.01f
        val phi = wallE.pos.phi() - pos.phi()
        val theta = wallE.pos.theta() - pos.theta()
        pos.y += phi*movementSpeed
        pos.x += theta*movementSpeed
    }
}

fun drawThings(pos: PVector, size: Float, app: PApplet) {
    val position = pos.toXyz()
    with(app) {
        pushMatrix()
        translate(position.x,position.y,position.z)
        sphere(size)
        popMatrix()
    }
}

data class Scrap(val pos:SphericalCoords, val size: Float= 3f) {
    fun draw(app: PApplet) {
        drawThings(pos, size, app)
    }
}

data class WallE(val pos: SphericalCoords, val size: Float = 5f, var rot: Float = 0f) {

    fun draw(app : PApplet) {
        drawThings(pos, size, app)
    }

    fun randUpdate() {
        pos.x += 0.01f
        pos.y += 0.02f
    }
    fun update(input: Input) {
        val movementSpeed = 0.01f
        val lookSpeed = 0.01f

//        var isMoving = false
        when {
//            input.isUp -> isMoving = true
//            input.isDown -> pos.x -= movementSpeed
//            input.isLeft -> rot += lookSpeed
//            input.isRight -> rot -= lookSpeed
            input.isUp -> pos.x += movementSpeed
            input.isDown -> pos.x -= movementSpeed
            input.isLeft -> pos.y += lookSpeed
            input.isRight -> pos.y -= lookSpeed//                pos.x

        }
//         actually move!!!
//        val wallePos = pos.toXyz()
//        val u = wallePos.copy().normalize()
////        val x = wallePos.copy().add(PVector(0.000000000000000000000000001f,0f,0f))
////        val x = u
////        val newPosCart = u * u.dot(x) + u.cross(x) * cos(rot) + u.cross(x) * sin(rot)
//        val a = wallePos.add(PVector(0.01f, 0f, 0f))

//        pos.set(pos.toXyz().add(0f,0f,1f).toSpherical())
//        pos.set(wallePos.toSpherical())

    }
    fun checkCollision(app: PApplet, toCheckPos: PVector, toCheckSize: Float): Boolean {
        val wallePos = pos.toXyz()
        with(app) {
            return dist(wallePos, toCheckPos.toXyz()) < size+toCheckSize
        }
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
    val enemies = mutableListOf<Enemy>()
    val scraps = mutableListOf<Scrap>()
    val wallE = WallE(SphericalCoords(0f,0f, EARTH_RADIUS+2f))
    val spaceWallE = WallE(SphericalCoords(0f, 0f, EARTH_RADIUS*3f), 10f)
    val moon = WallE(SphericalCoords(0f, 0f, EARTH_RADIUS*5f), 50f)
    var bombWallE: WallE? = null
    val scrapCounter = 0


    override fun setup() {
        server_udp_socket.setSoTimeout(1)
        frameRate(60F)
        addEnemy()
        addEnemy()
    }

    override fun settings() {
        fullScreen(P3D)
    }

    fun addEnemy() {
        var randStartX = Random.nextFloat()*2f*Math.PI
        var randStartY = Random.nextFloat()*2f*Math.PI
        val enemy = Enemy(SphericalCoords(randStartX.toFloat(), randStartY.toFloat(), EARTH_RADIUS+2f))
        enemies.add(enemy)
    }


    fun camTopDown() {
        var cartWallE: PVector = if(player == 1)
            wallE.pos.toXyz()
        else
            spaceWallE.pos.toXyz()
        val centre = cartWallE
        var eyepos = cartWallE * 1.2f
        if(player == 1)
                eyepos = cartWallE * 2f
        val up = PVector(0f,1f,0f)
        camera(eyepos.x, eyepos.y, eyepos.z, centre.x, centre.y, centre.z, up.x,up.y,up.z)
    }

    fun camTopDownAngled() {
        val cartWallE = spaceWallE.pos.toXyz()
        val wallE2 = spaceWallE.pos.copy()
//        wallE2.x += 5f
//        wallE2.y += 5f
        val centre = cartWallE
        var eyepos = wallE2.toXyz() * 2f
        val up = PVector(0f,1f,0f)
//        eyepos = up.cross(eyepos)
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
        if(key == ' ') {
//            println("ahhh")
            bombWallE = WallE(spaceWallE.pos.copy(), 5f)
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

    fun handleCollisions() {

        enemies.forEach {
            if (wallE.checkCollision(this, it.pos, it.size)) {

            }
        }

        scraps.forEach {
            if (wallE.checkCollision(this, it.pos, it.size)) {
//                scrapCount += 1
            }
        }
    }

    var bombTime = 1000f
    var bombStartTime = 0L
    var isExploding = false

    fun updateMovements() {
        enemies.forEach { it.update(wallE) }
        if(player == 1) {
            spaceWallE.randUpdate()
            wallE.update(input)
        }
        else {
            wallE.randUpdate()
            spaceWallE.update(input)
        }

        if(!isExploding && bombWallE != null) {
            bombWallE!!.pos.z -= 1f
            if (bombWallE!!.pos.z < EARTH_RADIUS) {
                isExploding = true
                bombStartTime = System.currentTimeMillis()
            }
        }
        moon.pos.x += 0.001f
    }

    fun runGame() {
        background(0f)
        setupCam()
//        val wallEReal = wallE.pos.toXyz()*1.1f
//        pointLight(255f, 255f, 153f, wallEReal.x, wallEReal.y, wallEReal.z);
//        directionalLight(51f, 102f, 126f, -1f, 0f, 0f);
        pushMatrix()
        updateMovements()
        fill(0f, 0f, 200f)
        sphere(EARTH_RADIUS)
        fill(255f)
//        noStroke()
        spaceWallE.draw(this)
        wallE.draw(this)
        val bombGrow = 0.01f
        if(isExploding) {
            bombWallE?.pos?.let {
                with (it.toXyz()) {
                    translate(x,y,z)
                    fill(255f,255f,8f)
                    sphere((System.currentTimeMillis()-bombStartTime) * bombGrow)
                }
            }
            if(System.currentTimeMillis() - bombStartTime >= bombTime) {
                bombWallE = null
                isExploding = false
            }
        } else bombWallE?.draw(this)
        enemies.forEach{
            it.draw(this)
        }
        moon.draw(this)
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
            server_udp_port = json_msg["server_port"] as Int
            player = json_msg["player"] as Int
            print(player)

            //server_udp_socket.connect(InetAddress.getByName(HOST), server_udp_port)
            // open local udp socket and connect to remove server udp port
            val client_udp_port = server_udp_socket.localPort
            // send our port to server
            server_tcp_socket.outputStream.write(("{\"port\":$client_udp_port}").toByteArray())

            // rx initial game state
           // val buffer = ByteArray(4096)
            // val packet = DatagramPacket(buffer, buffer.size)
            //server_udp_socket.receive(packet)
            // TODO: parse game state and use it to init next screen

            ///  Example tx echo to server
            //val tx_packet = DatagramPacket(packet.data, packet.data.size, InetAddress.getByName(HOST), server_udp_port)
            //server_udp_socket.send(tx_packet)

            // TODO: gameloop should include reading from UDP socket and writing state to server
        }

        gameScreen = 1
    }

    var counter = 0


    override fun draw() {

        if (gameScreen == 0) {
            homeScreen()
        } else {
            val deltaT = System.nanoTime() - timeSinceLastUpdate
            runGame()
            timeSinceLastUpdate = System.nanoTime()
            if (deltaT >= TIME_BETWEEN_PACKET_UPDATE) {
                // tx state and rx state
                timeSinceLastUpdate = 0
                // tx
                val coords = if (player==1) wallE.pos else spaceWallE.pos
                val tx_buffer = "{\"theta\":${coords.x}, \"phi\":${coords.y}, \"bomb\": ${bombWallE != null}}".toByteArray()
                val tx_packet = DatagramPacket(tx_buffer, tx_buffer.size, InetAddress.getByName(HOST), server_udp_port)
                tx_udp_socket.send(tx_packet)
                // rx
                try {
                    val rx_buffer = ByteArray(4096)
                    val rx_packet = DatagramPacket(rx_buffer, rx_buffer.size)
                    server_udp_socket.receive(rx_packet)
                    val rx = JSONObject(String(rx_packet.data))
                    other_theta = rx["theta"] as Float
                    other_phi = rx["phi"] as Float
                    bomb = rx["bomb"] as Boolean
                    // TODO: update state with other player
                } catch (e: SocketTimeoutException) {

                }
            }
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