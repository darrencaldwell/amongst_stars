import com.jogamp.opengl.math.Quaternion
import processing.core.PApplet
import processing.core.PVector
import org.json.*
import java.net.*
import java.util.*
import processing.core.PVector.dist
import kotlin.math.*
import kotlin.random.Random

import glm_.glm
import glm_.mat4x4.Mat4
import glm_.quat.Quat
import glm_.vec3.Vec3
import glm_.vec3.swizzle.xyz
import glm_.vec4.Vec4
import glm_.vec4.swizzle.xyz


const val EARTH_RADIUS = 100f
const val HOST = "192.168.24.126"
const val HOST_TCP_PORT = 25565
const val MAGIC_ROOM_ID = 1337
const val ENABLE_MULTIPLAYER = false
var gameScreen = 0
var timeSinceLastUpdate: Long = 0
// ms
const val TIME_BETWEEN_PACKET_UPDATE: Long = 1 * 1000000

var server_udp_port: Int = -1
val server_udp_socket = DatagramSocket()
val tx_udp_socket = DatagramSocket()

//var player = -1
var player = 1

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



//fun SphericalCoords.toXyz(): PVector {
//    val x1 = sin(theta()) * cos(phi())
//    val y1 = sin(theta()) * sin(phi())
//    val z1 = cos(theta())
//    val ret = PVector(x1,y1,z1)
//    ret.mult(r())
//    return ret
//}
//
//fun PVector.toSpherical(): SphericalCoords {
//    val r = sqrt(x.pow(2) + y.pow(2) + z.pow(2))
//    val phi = acos(z / r)
//    if (x == 0f) return SphericalCoords(0f, phi, r)
//    val theta = atan(y / x)
//
//    return SphericalCoords(theta, phi, r)
//}


data class Enemy(val pos:SphericalCoords, val size: Float=5f, var rot: Float=0f) {
    fun draw(app : PApplet) {
        drawThings(pos, size, app)
    }

    fun update(wallE: WallE) {
        val movementSpeed = 0.01f
        // todo!
//        val phi = wallE.pos.phi() - pos.phi()
//        val theta = wallE.pos.theta() - pos.theta()
//        pos.y += phi*movementSpeed
//        pos.x += theta*movementSpeed
    }
}

fun drawThings(pos: PVector, size: Float, app: PApplet) {
//    val position = pos
    with(app) {
        pushMatrix()
        translate(pos.x,pos.y,pos.z)
        sphere(size)
        popMatrix()
    }
}

data class Scrap(val pos:SphericalCoords, val size: Float= 3f) {
    fun draw(app: PApplet) {
        drawThings(pos, size, app)
    }
}

val globalUp = Vec3(1,0,0)
data class WallE(val pos: PVector, val size: Float = 5f, var orientation: Quat, var rot: Float = 0f) {

    companion object {
        @JvmStatic
        fun at_radius(r: Float, size: Float = 5f): WallE {
            val pos = PVector(0f,0f,r)
            // start looking north on globe
            // player up is z axis (TOWARD us)

//            // setting this to y makes us move around the equator
//            // setting this to x makes us move around the poles
//            // we lie on the z axis so that makes no sense
//            val up = globalUp

            val p = Vec3(pos.x,pos.y,pos.z)
            val upWorld = p.normalize()

            // look upward lets say
            val direction = Vec3(0,1,0)
            val orientation = glm.quatLookAt(direction, upWorld)
//            val orientation = Quat().angleAxis(90f, upWorld)
            return WallE(pos, size, orientation)

        }
    }

    fun draw(app : PApplet) {
        drawThings(pos, size, app)
//        val eyepos = 5f
//        app.fill(0f)
//        drawThings(PVector(pos.x, pos.y + 5f, pos.z), 2f, app)

    }

    fun randUpdate() {
        pos.x += 0.01f
        pos.y += 0.02f
    }
    fun update(input: Input, app: PApplet) {
        val movementSpeed = 0.01f
        val lookSpeed = 1f

        // old controller
//        if (input.isUp) pos.x += movementSpeed
//        if (input.isDown ) pos.x -= movementSpeed
        var drot = 0f
        if (input.isLeft ) drot -= lookSpeed
        if (input.isRight) drot += lookSpeed


//        print(start)
        val startHeight = pos.mag()

        // get our position vector in world coords
        val p = Vec4(pos.x,pos.y,pos.z, 1f)

        // since the planet is centred, our position vector is the same as the local up axis
        val upWorld = p.xyz.normalize()

//        val upPlayer = Vec4(0,0,1,1)

        // rotate orientation by yaw

        orientation = orientation + Quat().angleAxis(drot, upWorld.xyz)
        print("drot=$drot")

        val upGlobal = globalUp

        // move along look vector (tangential)
        val lookDir = (orientation.toMat4() * Vec4(upWorld, 1)).normalize()
        val speed = 0.5f
        val afterMove = p + lookDir * speed

        with (app) {
            val p2 = p + lookDir * 10
            push()
            stroke(255f,255f,0f)
            strokeWeight(10f)
            line(p.x,p.y,p.z,p2.x,p2.y,p2.z)
            pop()
        }

        // move down back to surface of sphere
        val newUpWorld = afterMove.xyz.normalize()
        val onSphere = newUpWorld * startHeight

        val finalPos = onSphere

        // take rotation from old up to new up
        // this reorients the player in world space
        val axis = glm.cross(upWorld, newUpWorld).normalize()
        val angle = glm.angle(upWorld, newUpWorld)
        val rotationFromMove =  Quat().angleAxis(angle, axis)
        println("axis=$axis angle=$angle")
        orientation = orientation + rotationFromMove

        // undo yaw adjustment
        orientation = orientation + Quat().angleAxis(-drot, newUpWorld.xyz)

        pos.set(finalPos.x,finalPos.y,finalPos.z)


//        val rotation = Mat4().rotateX(-pos.phi()).rotateY(-pos.theta()).translate(p)

        // world to player
//        val wtp = Mat4().translate(0f,0f,-pos.r()).rotateY(-pos.theta()).rotateZ(-pos.phi())
//        val ptw = wtp.inverse()
//        val speed = 1f
//        val vel = Vec4(cos(rot),sin(rot),1f, 0f) * speed
//        val pPrimePlayer = (wtp * p) + vel
//        val pPrime = ptw * pPrimePlayer
////        val dehomog = pPrime.xyz / pPrime.w
//        print("p=$p p'=$pPrime")

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









//
        // rotate the velocity vector
        // player rotation
//        val look = Mat4().rotateZ(rot)
        // always go forward
//        val movement = ptw *
//
//        println(movement)


//        val newPos = p + movement.xyz * 10000
//        app.stroke(255f)
//        app.strokeWeight(5f)
//        println(rot)
//        app.line(p.x,p.y,p.z,newPos.x,newPos.y,newPos.z)
//        app.noStroke()
//        val spherical = PVector(pPrime.x,pPrime.y,pPrime.z).toSpherical()
//
////        val oldX = spherical.y
////        // bad maths alert, something wack has happened here
////        spherical.x = spherical.y
////        spherical.y = oldX
////
//        // hard adjust of r coord to lock us to fixed radius orbit
//        spherical.z = r
//        print("end = $spherical")

        // don't need to dehomegenise, w is 1
//        pos.set(PVector(pPrime.x,pPrime.y,pPrime.z))




//        val p = SimpleMatrix(3,1,false, floatArrayOf(wallePos.x,wallePos.y,wallePos.z))
//
//        print(matrix)
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
}

fun checkCollision(app: PApplet, aPos: PVector, aSize: Float, bPos: PVector, bSize: Float): Boolean {
    with(app) {
        return dist(aPos, bPos) < aSize+bSize
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

    var cameraMode: CameraMode = CameraMode.TOPDOWN
    val input: Input = Input()
    val enemies = mutableListOf<Enemy>()
    val scraps = mutableListOf<Scrap>()
    val wallE = WallE.at_radius(EARTH_RADIUS+2f)
    val spaceWallE = WallE.at_radius(EARTH_RADIUS*3f, 35f)
    val moon = WallE.at_radius(EARTH_RADIUS*5f, 50f)
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
            wallE.pos
        else
            spaceWallE.pos
        val centre = cartWallE
        var eyepos = cartWallE * 1.5f
        if(player == 1)
            eyepos = cartWallE * 2f

//        val centre = PVector(0f,0f,0f)
//        val eyepos = PVector(0f, 0f, EARTH_RADIUS * 2.5f)
        val up = PVector(0f,1f,0f)
        camera(eyepos.x, eyepos.y, eyepos.z, centre.x, centre.y, centre.z, up.x,up.y,up.z)
    }

    fun camTopDownAngled() {
////        val cartWallE = spaceWallE.pos.copy()
////        val wallE2 = spaceWallE.pos.copy()
////        wallE2.x += 5f
////        wallE2.y += 5f
//        val centre = cartWallE
//        var eyepos = wallE2 * 2f
//        val up = PVector(0f,1f,0f)
////        eyepos = up.cross(eyepos)
//        camera(eyepos.x, eyepos.y, eyepos.z, centre.x, centre.y, centre.z, up.x,up.y,up.z)
    }

    fun camBottomUp() {
        val cartWallE = wallE.pos
        val eyepos = cartWallE
        val centre = cartWallE * 2f
        val up = PVector(0f,1f,0f)
        camera(eyepos.x, eyepos.y, eyepos.z, centre.x, centre.y, centre.z, up.x,up.y,up.z)
    }

    fun camFps() {
        val cartWallE = wallE.pos
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
            bombWallE = createBombWallE()
        }
        if(key == 'b' && player == 1) {
            cameraMode = CameraMode.BOTTOMUP
        }
        if(key == 't' && player == 1) {
            cameraMode = CameraMode.TOPDOWN
        }
    }

    fun createBombWallE(): Nothing = TODO() // WallE(spaceWallE.pos.copy(), 10f)


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

    var killedEnemies = mutableListOf<Enemy>()
    var wallELives = 50
    var invincibleTime = 1000f
    var lastHit = 0L

    fun handleCollisions() {
        enemies.forEach {
            if (checkCollision(this, wallE.pos, wallE.size, it.pos, it.size)) {
                if(System.currentTimeMillis() - lastHit > invincibleTime) {
                    lastHit = System.currentTimeMillis()
                    wallELives--
                    if(wallELives == 0) {
                        text("GAME OVER", width/2f, height/2f)
                        System.exit(0)
                    }
                }
            }
            if (bombWallE != null && checkCollision(this, bombWallE!!.pos, currExplosionSize, it.pos, it.size)) {
                killedEnemies.add(it)
            }
        }
        killedEnemies.forEach{
            enemies.remove(it)
        }
        killedEnemies.clear()
        scraps.forEach {
            if (checkCollision(this, wallE.pos, wallE.size, it.pos, it.size)) {
//                scrapCount += 1
            }
        }
    }

    var bombTime = 1000f
    var bombStartTime = 0L
    var isExploding = false

    fun updateMovements() {
//        enemies.forEach { it.update(wallE) }

        if(player == 1) {
            spaceWallE.pos.x = other_theta
            spaceWallE.pos.y = other_phi
            if(bomb && bombWallE == null) {
                bombWallE = createBombWallE()
            }
            wallE.update(input, this)
        }
        else {
            wallE.pos.x = other_theta
            wallE.pos.y = other_phi
            spaceWallE.update(input, this)
        }

        if(!isExploding && bombWallE != null) {
//            bombwa
            TODO()
//            bombWallE!!.pos.z -= 2.5f
//            if (bombWallE!!.pos.z < EARTH_RADIUS) {
//                isExploding = true
//                bombStartTime = System.currentTimeMillis()
//            }
        }
        handleCollisions()
        moon.pos.x += 0.001f
    }

    var currExplosionSize = 0f
    var spawningTime = 10*1000f
    var lastSpawnTime = System.currentTimeMillis()
    fun runGame() {
        println(enemies.size)
//        System.out.println(System.currentTimeMillis()-lastSpawnTime)
//        System.out.println(lastSpawnTime)
//        System.out.println(spawningTime)
        if((System.currentTimeMillis()-lastSpawnTime) > spawningTime) {
            addEnemy()
            lastSpawnTime = System.currentTimeMillis()
        }
        background(0f)
        setupCam()
        text("LIVES: " + wallELives, width/2f, height/2f)

        noStroke()

        pushMatrix()
        val bombGrow = 0.01f
        if(isExploding) {
            bombWallE?.pos?.let {
                pushMatrix()
                translate(it.x,it.y,it.z)
                fill(255f,255f,8f)
                currExplosionSize = (System.currentTimeMillis()-bombStartTime).toFloat() * bombGrow
                sphere(currExplosionSize)
                fill(255f)
                popMatrix()

            }
            if(System.currentTimeMillis() - bombStartTime >= bombTime) {
                bombWallE = null
                isExploding = false
            }
        }

        popMatrix()

        val wallEReal = wallE.pos*1.1f
        val ambience = 35f
        ambientLight(ambience,ambience,ambience)
        pointLight(255f, 255f, 153f, wallEReal.x, wallEReal.y, wallEReal.z);
        directionalLight(51f, 102f, 126f, -1f, 0f, 0f);
//        pushMatrix()
        updateMovements()
        fill(0f, 0f, 200f)
        stroke(255f)
        sphere(EARTH_RADIUS)
        noStroke()
        // space wallE is pink
        // todo: uncomment!
//        fill(222f,165f,164f)
//        spaceWallE.draw(this)


        // earth wallE is grey
        fill(222f)
        wallE.draw(this)
        // enemies are red
        fill(255f,0f,0f)
        enemies.forEach{
            it.draw(this)
        }
        // moon is white
        fill(255f)
        moon.draw(this)

        // bombs are orange
        if (!isExploding) {
            fill(255f,50f,0f)
            bombWallE?.draw(this)
        }
//        popMatrix()
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
            if (ENABLE_MULTIPLAYER) {

                timeSinceLastUpdate = System.nanoTime()
                if (deltaT >= TIME_BETWEEN_PACKET_UPDATE) {
                    // tx state and rx state
                    timeSinceLastUpdate = 0
                    // tx
                    val coords = if (player==1) wallE.pos else spaceWallE.pos
//                val thetaStr = "%.${scale}f".format(input)
                    val tx_buffer = "{\"theta\":${(coords.x * 1000).toInt()}, \"phi\":${(coords.y * 1000).toInt()}, \"bomb\": ${bombWallE != null}}".toByteArray()
                    val tx_packet = DatagramPacket(tx_buffer, tx_buffer.size, InetAddress.getByName(HOST), server_udp_port)
                    tx_udp_socket.send(tx_packet)
                    // rx
                    try {
                        val rx_buffer = ByteArray(4096)
                        val rx_packet = DatagramPacket(rx_buffer, rx_buffer.size)
                        server_udp_socket.receive(rx_packet)
                        val rx = JSONObject(String(rx_packet.data))
                        other_theta = rx["theta"] as Int / 1000f
                        other_phi = rx["phi"] as Int / 1000f
                        bomb = rx["bomb"] as Boolean
                        // TODO: update state with other player
                    } catch (e: SocketTimeoutException) {

                    }
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

