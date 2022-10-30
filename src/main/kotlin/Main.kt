import processing.core.PApplet
import processing.core.PVector
import java.util.Spliterators
import kotlin.math.*

//import ejml
//import org.ejml.

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
fun SphericalCoords.theta(): Float = this.x
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
    val theta = atan(x/z)
    val phi = acos(y/r)
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

data class WallE(val pos: SphericalCoords, val size: Float = 5f, var rot: Float = 0f) {
    fun draw(app: PApplet) {
        val position = pos.toXyz()
        with(app) {
            pushMatrix()
            translate(position.x,position.y,position.z)
            circle(0f,0f,size)
            fill(255f,0f,0f)
            circle(sin(rot),cos(rot),2f)
            popMatrix()
        }
    }

    var angle = 0.0f
    fun update(input: Input) {
//        val movementSpeed = 0.01f
//        angle += 0.01f
//        val lookSpeed = 0.01f

        when {
            input.isUp -> pos.x += 0.01f
            input.isDown -> pos.x -= 0.01f
            input.isLeft -> pos.y += 0.01f
            input.isRight -> pos.y -= 0.01f
        }


//        // actually move!!!!
//        // translate to wallE position
//        val wallEPos = pos.toXyz()
////        val up = wallEPos.copy().normalize()
////        wallEPos.x * up.x +
//        // new point is wallE pos + 1 (in X let's say)
////        val newPos = pos.copy().toXyz()
////            .rotateAngleAxis(angle, PVector(0f,1f,0f))
////            .toSpherical()
//
////        angle += 0.01f
//
//        val newPos = wallEPos
////            // go to wallE reference frame
////            .rotateAngleAxis(pos.theta(), PVector(0f,1f,0f))
////            .rotateAngleAxis(pos.phi(), PVector(0f,0f,1f))
////            .rotateAngleAxis(rot, PVector(0f,1f,0f))
////            // step forward by 1
////            .add(100f,0f,0f)
////            .rotateAngleAxis(-rot, PVector(0f,1f,0f))
////            // go back to world coords
////            .rotateAngleAxis(-pos.phi(), PVector(0f,0f,1f))
////            .rotateAngleAxis(-pos.theta(), PVector(0f,1f,0f))
//            .toSpherical()
//
////        newPos.z = EARTH_RADIUS + 2
//
//        pos.set(newPos)
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

    var theta = 1f
    var phi = 0f

    override fun draw() {
        rotate(radians(90f))
        background(0f)
//        val eyepos =

//        camera(cartWallE.x, cartWallE.y, cartWallE.z, eyepos.x, eyepos.y, eyepos.z, up.x,up.y,up.z)
        setupCam()
        pushMatrix()
//        val pos = PVector(width.toFloat()/2f, height.toFloat()/2f,100f)
//        with(pos) {
//            translate(x,y,z)
//        }

        fill(0f, 0f, 200f)
        pushMatrix()
        rotateX(radians(90f))
        sphere(EARTH_RADIUS)
        popMatrix()
        fill(255f)


//        // test
//        pushMatrix()
////        theta+=0.1f
//        phi+=0.1f
//        val testPos = SphericalCoords(theta, phi, EARTH_RADIUS+1).toXyz()
//        translate(testPos.x, testPos.y,testPos.z)
//        circle(0f,0f,50f)
//        popMatrix()



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