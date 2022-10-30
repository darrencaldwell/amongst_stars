import processing.core.PApplet
import processing.core.PVector
import java.util.InputMismatchException
import kotlin.math.cos
import kotlin.math.sin


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

data class WallE(val pos: SphericalCoords, val size: Float = 5f, var rot: Float = 0f) {
    fun draw(app: PApplet) {
        val position = pos.toXyz()
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

        var isMoving = false
        when {
            input.isUp -> isMoving = true
//            input.isDown -> pos.x -= movementSpeed
            input.isLeft -> rot += lookSpeed
            input.isRight -> rot -= lookSpeed
        }

        // actually move!!!!
        


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

    operator fun PVector.times(other: Float) = PVector.mult(this, other)

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
        val up = PVector(0f,1f,0f)
        camera(eyepos.x, eyepos.y, eyepos.z, centre.x, centre.y, centre.z, up.x,up.y,up.z)
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

    override fun draw() {
        background(0f)
        val eyepos =

//        camera(cartWallE.x, cartWallE.y, cartWallE.z, eyepos.x, eyepos.y, eyepos.z, up.x,up.y,up.z)
        setupCam()
        pushMatrix()
//        val pos = PVector(width.toFloat()/2f, height.toFloat()/2f,100f)
//        with(pos) {
//            translate(x,y,z)
//        }
        fill(0f, 0f, 200f)
        sphere(EARTH_RADIUS)
        fill(255f)
        wallE.draw(this)
        moon.draw(this)
        moon.pos.x += 0.01f
        wallE.update(input)
        popMatrix()
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