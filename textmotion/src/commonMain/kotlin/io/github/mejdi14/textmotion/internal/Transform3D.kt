package io.github.mejdi14.textmotion.internal

import androidx.compose.ui.graphics.Matrix
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Builds CSS-like 3D transforms (rotateX/rotateY with `perspective`) as a projective 2D matrix
 * usable in `DrawScope.withTransform { transform(matrix) }` on every platform. Reuse one instance
 * per effect to avoid per-frame allocation.
 *
 * Conventions follow CSS: y points down, positive rotationX tilts the top edge away from the
 * viewer, rotations are applied Z, then Y, then X (`rotateX() rotateY() rotateZ()`), translation is
 * applied after rotation, and [perspective] is the CSS `perspective` distance in px (0 = none).
 */
internal class Transform3D {
    val matrix: Matrix = Matrix()

    fun set(
        pivotX: Float,
        pivotY: Float,
        translationX: Float = 0f,
        translationY: Float = 0f,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        rotationX: Float = 0f,
        rotationY: Float = 0f,
        rotationZ: Float = 0f,
        perspective: Float = 0f,
    ): Matrix {
        val d2r = PI.toFloat() / 180f
        val cx = cos(rotationX * d2r); val sx = sin(rotationX * d2r)
        val cy = cos(rotationY * d2r); val sy = sin(rotationY * d2r)
        val cz = cos(rotationZ * d2r); val sz = sin(rotationZ * d2r)
        // R = Rx * Ry * Rz (CSS rotateX rotateY rotateZ), only first two columns needed (z = 0 input).
        // Rz columns: (cz, sz, 0), (-sz, cz, 0)
        // Ry * v: (cy*x + sy*z, y, -sy*x + cy*z)
        // Rx * v: (x, cx*y - sx*z, sx*y + cx*z)
        fun rot(x0: Float, y0: Float, out: FloatArray) {
            val x1 = cy * x0; val y1 = y0; val z1 = -sy * x0
            out[0] = x1
            out[1] = cx * y1 - sx * z1
            out[2] = sx * y1 + cx * z1
        }
        val c1 = tmp1; val c2 = tmp2
        rot(cz * scaleX, sz * scaleX, c1)
        rot(-sz * scaleY, cz * scaleY, c2)
        val a = c1[0]; val c = c1[1]; val f = c1[2]
        val b = c2[0]; val e = c2[1]; val g = c2[2]
        val pf = if (perspective > 0f) -f / perspective else 0f
        val pg = if (perspective > 0f) -g / perspective else 0f
        // Relative-coordinate projective matrix rows (x, y, w)
        val m00 = a + pivotX * pf; val m01 = b + pivotX * pg; val m02 = translationX + pivotX
        val m10 = c + pivotY * pf; val m11 = e + pivotY * pg; val m12 = translationY + pivotY
        val m20 = pf; val m21 = pg; val m22 = 1f
        // Absolute = M * T(-pivot)
        val v = matrix.values
        matrix.reset()
        v[0] = m00; v[4] = m01; v[12] = m02 - m00 * pivotX - m01 * pivotY
        v[1] = m10; v[5] = m11; v[13] = m12 - m10 * pivotX - m11 * pivotY
        v[3] = m20; v[7] = m21; v[15] = m22 - m20 * pivotX - m21 * pivotY
        return matrix
    }

    private val tmp1 = FloatArray(3)
    private val tmp2 = FloatArray(3)
}
